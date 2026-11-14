package org.shopflow.backend.application.usecase.dataset

import org.shopflow.backend.application.command.dataset.ImportKaggleDatasetCommand
import org.shopflow.backend.application.dto.dataset.DatasetImportResultDto
import org.shopflow.backend.domain.exception.DatasetImportException
import org.shopflow.backend.domain.exception.DomainException
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.RoleNotFoundException
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.domain.model.product.RatingAverage
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.shopflow.backend.domain.service.dataset.DatasetBatchImportPort
import org.shopflow.backend.domain.service.dataset.DatasetSourcePort
import org.shopflow.backend.domain.service.dataset.DatasetUuidConverter
import org.shopflow.backend.domain.service.dataset.OrderItemSeedRow
import org.shopflow.backend.domain.service.dataset.OrderSeedRow
import org.shopflow.backend.domain.service.dataset.ReviewSeedRow
import org.shopflow.backend.domain.service.dataset.UserEventSeedRow
import org.shopflow.backend.domain.service.storage.CategoryBannerGeneratorPort
import org.shopflow.backend.domain.service.storage.ImageStoragePort
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ImportKaggleDatasetUseCase(
    private val datasetSourcePort: DatasetSourcePort,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val datasetBatchImportPort: DatasetBatchImportPort,
    private val passwordHasher: PasswordHasher,
    private val imageStoragePort: ImageStoragePort,
    private val categoryBannerGeneratorPort: CategoryBannerGeneratorPort,
    private val timeProvider: TimeProvider,
) {
    fun execute(command: ImportKaggleDatasetCommand = ImportKaggleDatasetCommand()): DatasetImportResultDto {
        validateLimits(command)
        val startedAtNanos = System.nanoTime()

        val prepared = datasetSourcePort.prepareDatasetDirectory(command.forceDownload)
        val usersCsvPath = prepared.directory.resolve(USERS_CSV)
        val productsCsvPath = prepared.directory.resolve(PRODUCTS_CSV)

        if (!Files.isRegularFile(usersCsvPath) || !Files.isRegularFile(productsCsvPath)) {
            throw DatasetImportException(
                "Missing required CSV files ($USERS_CSV, $productsCsvPath) in '${prepared.directory}'.",
            )
        }

        val now = timeProvider.now()
        val userStats = importUsers(usersCsvPath, command.maxUsers, now)
        val productStats =
            importCategoriesAndProducts(
                productsCsvPath = productsCsvPath,
                maxProducts = command.maxProducts,
                uploadPlaceholders = command.uploadCategoryPlaceholders,
                refreshExistingImages = command.refreshExistingProductImages,
                now = now,
            )

        // Import downstream relational/event CSVs in strict foreign-key order after users & products.
        val orderStats =
            streamAndBatchInsert(
                csvPath = prepared.directory.resolve(ORDERS_CSV),
                parser = ::parseOrderSeedRow,
                idSelector = OrderSeedRow::id,
            ) { batch -> datasetBatchImportPort.insertOrdersIgnore(batch, now) }

        val orderItemStats =
            streamAndBatchInsert(
                csvPath = prepared.directory.resolve(ORDER_ITEMS_CSV),
                parser = ::parseOrderItemSeedRow,
                idSelector = OrderItemSeedRow::id,
            ) { batch -> datasetBatchImportPort.insertOrderItemsIgnore(batch, now) }

        val reviewsCsvPath = prepared.directory.resolve(REVIEWS_CSV)
        val reviewStats =
            streamAndBatchInsert(
                csvPath = reviewsCsvPath,
                parser = ::parseReviewSeedRow,
                idSelector = ReviewSeedRow::id,
            ) { batch -> datasetBatchImportPort.insertReviewsIgnore(batch, now) }

        val productReviewCountsUpdated =
            if (Files.isRegularFile(reviewsCsvPath)) {
                datasetBatchImportPort.syncProductReviewCounts()
            } else {
                0
            }

        val eventStats =
            streamAndBatchInsert(
                csvPath = prepared.directory.resolve(EVENTS_CSV),
                parser = ::parseUserEventSeedRow,
                idSelector = UserEventSeedRow::id,
            ) { batch -> datasetBatchImportPort.insertUserEventsIgnore(batch, now) }

        val durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
        return DatasetImportResultDto(
            sourceDirectory = prepared.directory.toAbsolutePath().toString(),
            downloadedFromRemote = prepared.downloadedFromRemote,
            cloudinaryLiveUploadUsed = productStats.cloudinaryLiveUploadUsed,
            usersInserted = userStats.inserted,
            usersSkipped = userStats.skipped,
            categoriesInserted = productStats.categoriesInserted,
            categoriesExisting = productStats.categoriesExisting,
            productsInserted = productStats.productsInserted,
            productsSkipped = productStats.productsSkipped,
            productsImageUpdated = productStats.productsImageUpdated,
            ordersInserted = orderStats.inserted,
            ordersSkipped = orderStats.skipped,
            orderItemsInserted = orderItemStats.inserted,
            orderItemsSkipped = orderItemStats.skipped,
            reviewsInserted = reviewStats.inserted,
            reviewsSkipped = reviewStats.skipped,
            productReviewCountsUpdated = productReviewCountsUpdated,
            userEventsInserted = eventStats.inserted,
            userEventsSkipped = eventStats.skipped,
            categoryImageUrls = productStats.categoryImageUrls,
            stagedCsvFiles = emptyMap(),
            durationMs = durationMs,
        )
    }

    private fun validateLimits(command: ImportKaggleDatasetCommand) {
        if (command.maxUsers != null && command.maxUsers < 0) {
            throw DomainValidationException("maxUsers cannot be negative.")
        }
        if (command.maxProducts != null && command.maxProducts < 0) {
            throw DomainValidationException("maxProducts cannot be negative.")
        }
    }

    private fun importUsers(
        usersCsvPath: Path,
        maxUsers: Int?,
        now: Instant,
    ): UserImportStats {
        if (maxUsers == 0) {
            return UserImportStats(0, 0)
        }
        val customerRole =
            roleRepository.findByName(Role.ROLE_CUSTOMER)
                ?: throw RoleNotFoundException("Default role '${Role.ROLE_CUSTOMER}' is not initialized.")

        val sharedPasswordHash = passwordHasher.encode(datasetSourcePort.defaultUserPassword())
        val batch = mutableListOf<User>()
        val seenIds = hashSetOf<UserId>()
        val seenEmails = hashSetOf<String>()
        var inserted = 0
        var skipped = 0

        CsvReaderSupport.streamRows(usersCsvPath, maxUsers) { row ->
            val candidate = parseUserCandidate(row, customerRole, sharedPasswordHash, now)
            if (candidate == null ||
                candidate.id in seenIds ||
                candidate.email.value.lowercase() in seenEmails
            ) {
                skipped++
            } else {
                seenIds += candidate.id
                seenEmails += candidate.email.value.lowercase()
                batch += candidate
                if (batch.size >= BATCH_SIZE) {
                    val flushStats = flushUserBatch(batch)
                    inserted += flushStats.inserted
                    skipped += flushStats.skipped
                    batch.clear()
                }
            }
        }

        if (batch.isNotEmpty()) {
            val flushStats = flushUserBatch(batch)
            inserted += flushStats.inserted
            skipped += flushStats.skipped
        }

        return UserImportStats(inserted, skipped)
    }

    private fun parseUserCandidate(
        row: CsvRowView,
        defaultRole: Role,
        sharedPasswordHash: HashedPassword,
        now: Instant,
    ): User? {
        val csvUserId = row.get("user_id")
        val rawEmail = row.get("email")
        if (csvUserId.isBlank() || rawEmail.isBlank()) {
            return null
        }
        return try {
            User(
                id = UserId.deterministic(csvUserId),
                email = Email.of(rawEmail.lowercase()),
                hashedPassword = sharedPasswordHash,
                roles = setOf(defaultRole),
                status = UserStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
                fullName = row.get("name").takeIf { it.isNotBlank() }?.take(User.MAX_FULL_NAME_LENGTH),
                city = row.get("city").takeIf { it.isNotBlank() }?.take(User.MAX_CITY_LENGTH),
            )
        } catch (_: DomainException) {
            null
        }
    }

    private fun flushUserBatch(batch: List<User>): UserImportStats {
        val existingIds = userRepository.findExistingIds(batch.map { it.id })
        val existingEmails =
            userRepository
                .findExistingEmails(batch.map { it.email.value.lowercase() })
                .mapTo(hashSetOf()) { it.lowercase() }

        val toInsert =
            batch.filter { candidate ->
                candidate.id !in existingIds && candidate.email.value.lowercase() !in existingEmails
            }
        if (toInsert.isNotEmpty()) {
            userRepository.saveAll(toInsert)
        }
        val skipped = batch.size - toInsert.size
        return UserImportStats(inserted = toInsert.size, skipped = skipped)
    }

    private fun importCategoriesAndProducts(
        productsCsvPath: Path,
        maxProducts: Int?,
        uploadPlaceholders: Boolean,
        refreshExistingImages: Boolean,
        now: Instant,
    ): ProductImportStats {
        if (maxProducts == 0) {
            return ProductImportStats(0, 0, 0, 0, 0, false, emptyMap())
        }

        val parsedRows = mutableListOf<ParsedProductRow>()
        var malformedRows = 0
        CsvReaderSupport.streamRows(productsCsvPath, maxProducts) { row ->
            val parsed = parseProductRow(row)
            if (parsed == null) {
                malformedRows++
            } else {
                parsedRows += parsed
            }
        }

        if (parsedRows.isEmpty()) {
            return ProductImportStats(0, 0, 0, malformedRows, 0, false, emptyMap())
        }

        val categoryNameBySlug = linkedMapOf<Slug, String>()
        parsedRows.forEach { row ->
            categoryNameBySlug.putIfAbsent(row.categorySlug, row.categoryName)
        }

        val existingCategories = categoryRepository.findAllBySlugs(categoryNameBySlug.keys)
        val categoriesToCreate =
            categoryNameBySlug
                .filterKeys { it !in existingCategories }
                .entries
                .mapIndexed { idx, (slug, name) ->
                    Category
                        .create(
                            id = CategoryId.deterministic(name),
                            name = name.take(Category.MAX_NAME_LENGTH),
                            slug = slug,
                            description = "Imported $name catalog",
                            displayOrder = existingCategories.size + idx + 1,
                            isActive = true,
                            now = now,
                        ).first
                }
        val savedCategories =
            if (categoriesToCreate.isNotEmpty()) {
                categoryRepository.saveAll(categoriesToCreate)
            } else {
                emptyList()
            }

        val resolvedCategories =
            linkedMapOf<Slug, Category>().apply {
                putAll(existingCategories)
                savedCategories.forEach { put(it.slug, it) }
            }

        val useLiveCloudinary = uploadPlaceholders && imageStoragePort.isConfigured()
        val categoryImageUrls =
            resolveCategoryImages(
                categories = resolvedCategories.values,
                useLiveCloudinary = useLiveCloudinary,
            )

        val productBatchStats =
            batchInsertProducts(
                parsedRows = parsedRows,
                resolvedCategories = resolvedCategories,
                categoryImageUrls = categoryImageUrls,
                now = now,
            )

        var updatedImages = 0
        if (refreshExistingImages && productBatchStats.skipped > 0) {
            resolvedCategories.values.forEach { category ->
                val targetUrl = categoryImageUrls[category.slug.value]
                if (targetUrl != null) {
                    updatedImages += productRepository.updateImageUrlByCategoryId(category.id, targetUrl)
                }
            }
        }

        return ProductImportStats(
            categoriesInserted = savedCategories.size,
            categoriesExisting = existingCategories.size,
            productsInserted = productBatchStats.inserted,
            productsSkipped = productBatchStats.skipped + malformedRows,
            productsImageUpdated = updatedImages,
            cloudinaryLiveUploadUsed = useLiveCloudinary,
            categoryImageUrls = categoryImageUrls,
        )
    }

    private fun parseProductRow(row: CsvRowView): ParsedProductRow? {
        val rawId = row.get("product_id")
        val rawName = row.get("product_name")
        val rawPrice = row.get("price")
        if (rawId.isBlank() || rawName.isBlank() || rawPrice.isBlank()) {
            return null
        }
        return try {
            val categoryName = row.get("category").ifBlank { "General" }
            val categorySlug = Slug.of(categoryName, Slug.CATEGORY_MAX_LENGTH)
            val sku = Sku.of("KGL-$rawId")
            val slug = buildUniqueProductSlug(rawName, rawId)
            val price = Money.of(rawPrice)
            val rating =
                row
                    .get("rating")
                    .takeIf { it.isNotBlank() }
                    ?.let { RatingAverage.ofClamped(it) }
                    ?: RatingAverage.ZERO
            val brand = row.get("brand").takeIf { it.isNotBlank() }
            ParsedProductRow(
                id = ProductId.deterministic(rawId),
                name = rawName.take(Product.MAX_NAME_LENGTH),
                categoryName = categoryName,
                categorySlug = categorySlug,
                sku = sku,
                slug = slug,
                description = buildProductDescription(brand, categoryName),
                price = price,
                averageRating = rating,
            )
        } catch (_: DomainException) {
            null
        }
    }

    private fun buildUniqueProductSlug(
        name: String,
        rawId: String,
    ): Slug {
        val idSuffix = Slug.of(rawId).value
        val maxPrefixLen = (Slug.MAX_LENGTH - idSuffix.length - 1).coerceAtLeast(1)
        val nameSlug =
            Slug
                .of(name, maxPrefixLen)
                .value
                .take(maxPrefixLen)
                .trimEnd('-')
        val combined = if (nameSlug.isEmpty()) idSuffix else "$nameSlug-$idSuffix"
        return Slug(combined)
    }

    private fun buildProductDescription(
        brand: String?,
        categoryName: String,
    ): String =
        if (brand != null) {
            "Brand: $brand | Category: $categoryName"
        } else {
            "Category: $categoryName"
        }

    private fun resolveCategoryImages(
        categories: Collection<Category>,
        useLiveCloudinary: Boolean,
    ): Map<String, String> =
        categories.associate { category ->
            val slug = category.slug.value
            val imageUrl =
                if (useLiveCloudinary) {
                    val pngBytes = categoryBannerGeneratorPort.generateBannerPng(category.name, slug)
                    imageStoragePort
                        .uploadImage(
                            fileBytes = pngBytes,
                            fileName = "$slug.png",
                            folder = "categories",
                            publicIdHint = "category-$slug",
                        ).url
                } else {
                    categoryBannerGeneratorPort.defaultFallbackUrl(slug)
                }
            slug to imageUrl
        }

    private fun batchInsertProducts(
        parsedRows: List<ParsedProductRow>,
        resolvedCategories: Map<Slug, Category>,
        categoryImageUrls: Map<String, String>,
        now: Instant,
    ): UserImportStats {
        val seenIds = hashSetOf<ProductId>()
        val seenSkus = hashSetOf<Sku>()
        val seenSlugs = hashSetOf<Slug>()
        val batch = mutableListOf<Product>()
        var inserted = 0
        var skipped = 0

        for (row in parsedRows) {
            val category = resolvedCategories[row.categorySlug]
            if (category == null || row.id in seenIds || row.sku in seenSkus || row.slug in seenSlugs) {
                skipped++
                continue
            }
            seenIds += row.id
            seenSkus += row.sku
            seenSlugs += row.slug

            val (product, _) =
                Product.create(
                    id = row.id,
                    categoryId = category.id,
                    name = row.name,
                    slug = row.slug,
                    sku = row.sku,
                    price = row.price,
                    description = row.description,
                    stockQuantity = DEFAULT_STOCK_QUANTITY,
                    averageRating = row.averageRating,
                    reviewCount = 0,
                    imageUrl = categoryImageUrls[row.categorySlug.value],
                    status = ProductStatus.ACTIVE,
                    now = now,
                )
            batch += product

            if (batch.size >= BATCH_SIZE) {
                val stats = flushProductBatch(batch)
                inserted += stats.inserted
                skipped += stats.skipped
                batch.clear()
            }
        }

        if (batch.isNotEmpty()) {
            val stats = flushProductBatch(batch)
            inserted += stats.inserted
            skipped += stats.skipped
        }

        return UserImportStats(inserted, skipped)
    }

    private fun flushProductBatch(batch: List<Product>): UserImportStats {
        val existingIds = productRepository.findExistingIds(batch.map { it.id })
        val existingSkus = productRepository.findExistingSkus(batch.map { it.sku })
        val existingSlugs = productRepository.findExistingSlugs(batch.map { it.slug })

        val toInsert =
            batch.filter { product ->
                product.id !in existingIds &&
                    product.sku !in existingSkus &&
                    product.slug !in existingSlugs
            }
        if (toInsert.isNotEmpty()) {
            productRepository.saveAll(toInsert)
        }
        return UserImportStats(
            inserted = toInsert.size,
            skipped = batch.size - toInsert.size,
        )
    }

    private fun <T> streamAndBatchInsert(
        csvPath: Path,
        parser: (CsvRowView) -> T?,
        idSelector: (T) -> String,
        batchWriter: (List<T>) -> Int,
    ): UserImportStats {
        if (!Files.isRegularFile(csvPath)) {
            return UserImportStats(0, 0)
        }

        val seenIds = hashSetOf<String>()
        val batch = ArrayList<T>(RELATIONAL_BATCH_SIZE)
        var inserted = 0
        var skipped = 0

        CsvReaderSupport.streamRows(csvPath) { row ->
            val parsed = parser(row)
            if (parsed == null) {
                skipped++
            } else {
                val rowId = idSelector(parsed)
                if (!seenIds.add(rowId)) {
                    skipped++
                } else {
                    batch += parsed
                    if (batch.size >= RELATIONAL_BATCH_SIZE) {
                        val batchInserted = batchWriter(batch)
                        inserted += batchInserted
                        skipped += (batch.size - batchInserted)
                        batch.clear()
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            val batchInserted = batchWriter(batch)
            inserted += batchInserted
            skipped += (batch.size - batchInserted)
        }

        return UserImportStats(inserted = inserted, skipped = skipped)
    }

    private fun parseOrderSeedRow(row: CsvRowView): OrderSeedRow? {
        val rawOrderId = row.get("order_id")
        val rawUserId = row.get("user_id")
        val orderStatus = row.get("order_status").take(MAX_STATUS_LENGTH)
        val orderDate = parseDatasetTimestamp(row.get("order_date")) ?: return null
        val totalAmount = parseNonNegativeMoney(row.get("total_amount")) ?: return null
        if (rawOrderId.isBlank() || rawUserId.isBlank() || orderStatus.isBlank()) {
            return null
        }
        return try {
            OrderSeedRow(
                id = DatasetUuidConverter.orderId(rawOrderId),
                userId = DatasetUuidConverter.userId(rawUserId),
                orderDate = orderDate,
                orderStatus = orderStatus,
                totalAmount = totalAmount,
            )
        } catch (_: DomainException) {
            null
        }
    }

    private fun parseOrderItemSeedRow(row: CsvRowView): OrderItemSeedRow? {
        val rawOrderItemId = row.get("order_item_id")
        val rawOrderId = row.get("order_id")
        val rawProductId = row.get("product_id")
        val rawUserId = row.get("user_id")
        val quantity = row.get("quantity").toIntOrNull()?.takeIf { it > 0 } ?: return null
        val itemPrice = parseNonNegativeMoney(row.get("item_price")) ?: return null
        val itemTotal = parseNonNegativeMoney(row.get("item_total")) ?: return null
        if (rawOrderItemId.isBlank() || rawOrderId.isBlank() || rawProductId.isBlank() || rawUserId.isBlank()) {
            return null
        }
        return try {
            OrderItemSeedRow(
                id = DatasetUuidConverter.orderItemId(rawOrderItemId),
                orderId = DatasetUuidConverter.orderId(rawOrderId),
                productId = DatasetUuidConverter.productId(rawProductId),
                userId = DatasetUuidConverter.userId(rawUserId),
                quantity = quantity,
                itemPrice = itemPrice,
                itemTotal = itemTotal,
            )
        } catch (_: DomainException) {
            null
        }
    }

    private fun parseReviewSeedRow(row: CsvRowView): ReviewSeedRow? {
        val rawReviewId = row.get("review_id")
        val rawOrderId = row.get("order_id")
        val rawProductId = row.get("product_id")
        val rawUserId = row.get("user_id")
        val rating = row.get("rating").toIntOrNull()?.takeIf { it in MIN_REVIEW_RATING..MAX_REVIEW_RATING } ?: return null
        val reviewDate = parseDatasetTimestamp(row.get("review_date")) ?: return null
        if (rawReviewId.isBlank() || rawOrderId.isBlank() || rawProductId.isBlank() || rawUserId.isBlank()) {
            return null
        }
        val reviewText = row.get("review_text").takeIf { it.isNotBlank() }
        return try {
            ReviewSeedRow(
                id = DatasetUuidConverter.reviewId(rawReviewId),
                orderId = DatasetUuidConverter.orderId(rawOrderId),
                productId = DatasetUuidConverter.productId(rawProductId),
                userId = DatasetUuidConverter.userId(rawUserId),
                rating = rating,
                reviewText = reviewText,
                reviewDate = reviewDate,
            )
        } catch (_: DomainException) {
            null
        }
    }

    private fun parseUserEventSeedRow(row: CsvRowView): UserEventSeedRow? {
        val rawEventId = row.get("event_id")
        val rawUserId = row.get("user_id")
        val rawProductId = row.get("product_id")
        val eventType = row.get("event_type").take(MAX_EVENT_TYPE_LENGTH)
        val eventTimestamp = parseDatasetTimestamp(row.get("event_timestamp")) ?: return null
        if (rawEventId.isBlank() || rawUserId.isBlank() || rawProductId.isBlank() || eventType.isBlank()) {
            return null
        }
        return try {
            UserEventSeedRow(
                id = DatasetUuidConverter.eventId(rawEventId),
                userId = DatasetUuidConverter.userId(rawUserId),
                productId = DatasetUuidConverter.productId(rawProductId),
                eventType = eventType,
                eventTimestamp = eventTimestamp,
            )
        } catch (_: DomainException) {
            null
        }
    }

    private fun parseDatasetTimestamp(raw: String): Instant? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return runCatching { Instant.parse(trimmed) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(trimmed.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    private fun parseNonNegativeMoney(raw: String): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return try {
            val scaled = BigDecimal(trimmed).setScale(2, RoundingMode.HALF_UP)
            scaled.takeIf { it >= BigDecimal.ZERO }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private data class UserImportStats(
        val inserted: Int,
        val skipped: Int,
    )

    private data class ParsedProductRow(
        val id: ProductId,
        val name: String,
        val categoryName: String,
        val categorySlug: Slug,
        val sku: Sku,
        val slug: Slug,
        val description: String,
        val price: Money,
        val averageRating: RatingAverage,
    )

    private data class ProductImportStats(
        val categoriesInserted: Int,
        val categoriesExisting: Int,
        val productsInserted: Int,
        val productsSkipped: Int,
        val productsImageUpdated: Int,
        val cloudinaryLiveUploadUsed: Boolean,
        val categoryImageUrls: Map<String, String>,
    )

    companion object {
        private const val BATCH_SIZE = 500
        private const val RELATIONAL_BATCH_SIZE = 2000
        private const val DEFAULT_STOCK_QUANTITY = 100
        private const val MAX_STATUS_LENGTH = 32
        private const val MAX_EVENT_TYPE_LENGTH = 50
        private const val MIN_REVIEW_RATING = 1
        private const val MAX_REVIEW_RATING = 5
        private const val USERS_CSV = "users.csv"
        private const val PRODUCTS_CSV = "products.csv"
        private const val ORDERS_CSV = "orders.csv"
        private const val ORDER_ITEMS_CSV = "order_items.csv"
        private const val REVIEWS_CSV = "reviews.csv"
        private const val EVENTS_CSV = "events.csv"
    }
}
