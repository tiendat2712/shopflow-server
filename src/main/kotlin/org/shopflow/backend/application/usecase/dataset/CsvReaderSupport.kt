package org.shopflow.backend.application.usecase.dataset

import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal class CsvRowView(
    private val headerIndex: Map<String, Int>,
    private val values: List<String>,
) {
    fun get(columnName: String): String =
        headerIndex[columnName.lowercase()]?.let { index ->
            values.getOrNull(index)?.trim() ?: ""
        } ?: ""
}

internal object CsvReaderSupport {
    fun streamRows(
        csvPath: Path,
        limit: Int? = null,
        consumer: (CsvRowView) -> Unit,
    ) {
        Files.newBufferedReader(csvPath, StandardCharsets.UTF_8).use { reader ->
            val headerFields = readNextRecord(reader) ?: return
            val headerIndex =
                headerFields
                    .mapIndexed { idx, rawHeader ->
                        rawHeader
                            .removePrefix("\uFEFF")
                            .trim()
                            .lowercase() to idx
                    }.toMap()

            var emitted = 0
            while (limit == null || emitted < limit) {
                val record = readNextRecord(reader) ?: break
                if (record.size == 1 && record[0].isBlank()) {
                    continue
                }
                consumer(CsvRowView(headerIndex, record))
                emitted++
            }
        }
    }

    fun countDataRows(csvPath: Path): Long {
        if (!Files.isRegularFile(csvPath)) {
            return 0L
        }
        var count = 0L
        streamRows(csvPath) { count++ }
        return count
    }

    private fun readNextRecord(reader: BufferedReader): List<String>? {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var sawAnyChar = false

        while (true) {
            val code = reader.read()
            if (code == -1) {
                if (!sawAnyChar) {
                    return null
                }
                fields += current.toString()
                return fields
            }
            sawAnyChar = true
            val ch = code.toChar()

            when {
                inQuotes -> {
                    if (ch == '"') {
                        reader.mark(1)
                        val next = reader.read()
                        if (next == '"'.code) {
                            current.append('"')
                        } else {
                            if (next != -1) {
                                reader.reset()
                            }
                            inQuotes = false
                        }
                    } else {
                        current.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    fields += current.toString()
                    current.setLength(0)
                }
                ch == '\r' -> {
                    reader.mark(1)
                    val next = reader.read()
                    if (next != '\n'.code && next != -1) {
                        reader.reset()
                    }
                    fields += current.toString()
                    return fields
                }
                ch == '\n' -> {
                    fields += current.toString()
                    return fields
                }
                else -> current.append(ch)
            }
        }
    }
}
