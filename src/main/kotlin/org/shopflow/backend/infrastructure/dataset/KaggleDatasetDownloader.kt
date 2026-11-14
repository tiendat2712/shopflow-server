package org.shopflow.backend.infrastructure.dataset

import org.shopflow.backend.domain.exception.DatasetImportException
import org.shopflow.backend.domain.service.dataset.DatasetSourcePort
import org.shopflow.backend.domain.service.dataset.PreparedDatasetDirectory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Base64
import java.util.zip.ZipInputStream

@Component
class KaggleDatasetDownloader(
    private val properties: DatasetProperties,
) : DatasetSourcePort {
    override fun defaultUserPassword(): String = properties.defaultUserPassword

    override fun prepareDatasetDirectory(forceDownload: Boolean): PreparedDatasetDirectory {
        val targetDir = Path.of(properties.localDir).toAbsolutePath().normalize()
        Files.createDirectories(targetDir)

        if (!forceDownload && hasRequiredCsvFiles(targetDir)) {
            return PreparedDatasetDirectory(directory = targetDir, downloadedFromRemote = false)
        }

        downloadAndExtract(targetDir)
        if (!hasRequiredCsvFiles(targetDir)) {
            throw DatasetImportException(
                "Downloaded Kaggle archive did not contain required files (users.csv, products.csv).",
            )
        }
        return PreparedDatasetDirectory(directory = targetDir, downloadedFromRemote = true)
    }

    private fun hasRequiredCsvFiles(directory: Path): Boolean {
        val usersFile = directory.resolve("users.csv")
        val productsFile = directory.resolve("products.csv")
        return Files.isRegularFile(usersFile) &&
            Files.size(usersFile) > 0L &&
            Files.isRegularFile(productsFile) &&
            Files.size(productsFile) > 0L
    }

    private fun downloadAndExtract(targetDir: Path) {
        val downloadUrl = "https://www.kaggle.com/api/v1/datasets/download/${properties.kaggleSlug.trim()}"
        val noRedirectClient =
            HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(30))
                .build()

        val initialBuilder =
            HttpRequest
                .newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/zip, application/octet-stream")
                .GET()

        if (properties.kaggleUsername.isNotBlank() && properties.kaggleKey.isNotBlank()) {
            val credentials = "${properties.kaggleUsername.trim()}:${properties.kaggleKey.trim()}"
            val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(StandardCharsets.UTF_8))
            initialBuilder.header("Authorization", "Basic $encoded")
        }

        val initialResponse =
            try {
                noRedirectClient.send(initialBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
            } catch (ex: Exception) {
                throw DatasetImportException("Failed to connect to Kaggle dataset endpoint: ${ex.message}", ex)
            }

        val zipStream: InputStream =
            when (initialResponse.statusCode()) {
                200 -> initialResponse.body()
                in 300..399 -> {
                    initialResponse.body().close()
                    val redirectLocation =
                        initialResponse
                            .headers()
                            .firstValue("Location")
                            .orElseThrow {
                                DatasetImportException("Kaggle returned redirect ${initialResponse.statusCode()} without Location header.")
                            }
                    followRedirect(URI.create(downloadUrl).resolve(redirectLocation))
                }
                else -> {
                    initialResponse.body().close()
                    throw DatasetImportException(
                        "Failed to download Kaggle dataset '${properties.kaggleSlug}' (HTTP ${initialResponse.statusCode()}). " +
                            "Provide KAGGLE_USERNAME/KAGGLE_KEY or place CSV files directly in '$targetDir'.",
                    )
                }
            }

        zipStream.use { stream ->
            extractCsvEntriesSafely(stream, targetDir)
        }
    }

    private fun followRedirect(targetUri: URI): InputStream {
        val followClient =
            HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
        val request =
            HttpRequest
                .newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofMinutes(3))
                .GET()
                .build()
        val response =
            try {
                followClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            } catch (ex: Exception) {
                throw DatasetImportException("Failed to download redirected Kaggle dataset archive: ${ex.message}", ex)
            }
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw DatasetImportException(
                "Failed to fetch Kaggle archive from redirected host (HTTP ${response.statusCode()}).",
            )
        }
        return response.body()
    }

    private fun extractCsvEntriesSafely(
        inputStream: InputStream,
        targetDir: Path,
    ) {
        val tempZip = Files.createTempFile("kaggle-dataset-", ".zip")
        try {
            inputStream.use { Files.copy(it, tempZip, StandardCopyOption.REPLACE_EXISTING) }
            ZipInputStream(Files.newInputStream(tempZip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                        val fileName = Path.of(entry.name).fileName.toString()
                        val destination = targetDir.resolve(fileName).normalize()
                        if (!destination.startsWith(targetDir)) {
                            throw DatasetImportException("Unsafe zip entry detected: ${entry.name}")
                        }
                        Files.copy(zis, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (ex: DatasetImportException) {
            throw ex
        } catch (ex: Exception) {
            throw DatasetImportException("Failed to extract Kaggle dataset archive: ${ex.message}", ex)
        } finally {
            Files.deleteIfExists(tempZip)
        }
    }
}
