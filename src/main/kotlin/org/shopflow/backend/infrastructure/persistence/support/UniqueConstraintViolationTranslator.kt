package org.shopflow.backend.infrastructure.persistence.support

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * Resolves database unique constraint violations to the corresponding business column name
 * without leaking internal table, index, or SQL details outside the infrastructure layer.
 */
@Component
class UniqueConstraintViolationTranslator(
    private val dataSource: DataSource,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val indexColumnCache = ConcurrentHashMap<Pair<String, String>, String>()
    private val loadedTables = ConcurrentHashMap.newKeySet<String>()
    private val metadataLock = Any()

    /**
     * Returns the violated business column name (e.g., `"email"`, `"slug"`, `"sku"`) if [ex]
     * represents a unique constraint collision on a tracked column of [expectedTable], or `null`
     * for non-unique integrity errors, `PRIMARY` key collisions, or unrecognized constraints.
     */
    fun resolveViolatedColumn(
        ex: DataIntegrityViolationException,
        expectedTable: String,
    ): String? {
        val inspection = inspectCauseChain(ex)
        val uniqueSqlEx = inspection.uniqueSqlEx
        if (uniqueSqlEx == null && ex !is DuplicateKeyException) {
            return null
        }

        val qualifiedConstraint =
            extractQualifiedConstraintName(
                hibernateConstraintEx = inspection.hibernateConstraintEx,
                uniqueSqlEx = uniqueSqlEx,
                fallbackMessage = ex.mostSpecificCause.message,
            ) ?: return null

        val (tableQualifier, indexName) = parseQualifiedConstraint(qualifiedConstraint)
        if (indexName.isBlank()) {
            return null
        }

        val normalizedTable = expectedTable.lowercase()
        if (tableQualifier != null && tableQualifier.lowercase() != normalizedTable) {
            return null
        }

        val normalizedIndex = indexName.lowercase()
        if (normalizedIndex == "primary" || normalizedIndex.endsWith("_pkey")) {
            return null
        }

        val resolvedColumn =
            KNOWN_INDEX_COLUMNS[normalizedTable to normalizedIndex]
                ?: resolveFromMetadata(normalizedTable, normalizedIndex)
                ?: return null

        val allowedColumns = ALLOWED_COLUMNS_BY_TABLE[normalizedTable] ?: return null
        return resolvedColumn.takeIf { it in allowedColumns }
    }

    private fun inspectCauseChain(root: Throwable): CauseInspection {
        var hibernateConstraintEx: ConstraintViolationException? = null
        var uniqueSqlEx: SQLException? = null
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())

        var current: Throwable? = root
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH && visited.add(current)) {
            if (hibernateConstraintEx == null && current is ConstraintViolationException) {
                hibernateConstraintEx = current
            }
            if (uniqueSqlEx == null && current is SQLException && isUniqueSqlException(current)) {
                uniqueSqlEx = current
            }
            if (hibernateConstraintEx != null && uniqueSqlEx != null) {
                break
            }
            current = current.cause
            depth++
        }
        return CauseInspection(hibernateConstraintEx = hibernateConstraintEx, uniqueSqlEx = uniqueSqlEx)
    }

    private fun isUniqueSqlException(sqlEx: SQLException): Boolean =
        sqlEx.errorCode == MYSQL_DUPLICATE_ENTRY_ERROR_CODE ||
            sqlEx.sqlState == POSTGRES_OR_ANSI_UNIQUE_VIOLATION

    private fun extractQualifiedConstraintName(
        hibernateConstraintEx: ConstraintViolationException?,
        uniqueSqlEx: SQLException?,
        fallbackMessage: String?,
    ): String? {
        val hibernateConstraint = hibernateConstraintEx?.constraintName?.trim()?.takeIf { it.isNotEmpty() }
        if (hibernateConstraint != null) {
            return hibernateConstraint
        }

        val candidateMessages = listOfNotNull(uniqueSqlEx?.message, fallbackMessage)
        for (message in candidateMessages) {
            val matched =
                FOR_KEY_REGEX
                    .findAll(message)
                    .lastOrNull()
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            if (!matched.isNullOrEmpty()) {
                return matched
            }
        }
        return null
    }

    private fun parseQualifiedConstraint(rawConstraint: String): Pair<String?, String> {
        val cleaned = rawConstraint.trim().trim('`', '"', '\'')
        val dotIndex = cleaned.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < cleaned.length - 1) {
            val tablePart = cleaned.substring(0, dotIndex).trim('`', '"', '\'')
            val indexPart = cleaned.substring(dotIndex + 1).trim('`', '"', '\'')
            tablePart to indexPart
        } else {
            null to cleaned
        }
    }

    private fun resolveFromMetadata(
        normalizedTable: String,
        normalizedIndex: String,
    ): String? {
        indexColumnCache[normalizedTable to normalizedIndex]?.let { return it }

        if (!loadedTables.contains(normalizedTable)) {
            synchronized(metadataLock) {
                if (!loadedTables.contains(normalizedTable)) {
                    loadTableUniqueIndexes(normalizedTable)
                }
            }
        }
        return indexColumnCache[normalizedTable to normalizedIndex]
    }

    private fun loadTableUniqueIndexes(normalizedTable: String) {
        val loadedSuccessfully =
            runCatching {
                // Borrow an independent connection so the metadata read never touches a tainted transaction.
                dataSource.connection.use { connection ->
                    connection.prepareStatement(UNIQUE_INDEX_METADATA_SQL).use { statement ->
                        statement.setString(1, normalizedTable)
                        statement.executeQuery().use { rs ->
                            while (rs.next()) {
                                val indexName = rs.getString("idx_name")?.trim()?.lowercase()
                                val columnName = rs.getString("col_name")?.trim()?.lowercase()
                                val columnCount = rs.getInt("col_count")
                                if (!indexName.isNullOrEmpty() && !columnName.isNullOrEmpty() && columnCount == 1) {
                                    indexColumnCache[normalizedTable to indexName] = columnName
                                }
                            }
                        }
                    }
                }
            }.onFailure { metadataEx ->
                logger.warn(
                    "Unable to load unique index metadata for table '{}': {}",
                    normalizedTable,
                    metadataEx.message,
                )
            }.isSuccess

        if (loadedSuccessfully) {
            loadedTables.add(normalizedTable)
        }
    }

    private data class CauseInspection(
        val hibernateConstraintEx: ConstraintViolationException?,
        val uniqueSqlEx: SQLException?,
    )

    companion object {
        private const val MAX_CAUSE_DEPTH = 16
        private const val MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062
        private const val POSTGRES_OR_ANSI_UNIQUE_VIOLATION = "23505"

        private val FOR_KEY_REGEX = Regex("""for key ['`]([^'`]+)['`]""", RegexOption.IGNORE_CASE)

        private const val UNIQUE_INDEX_METADATA_SQL =
            """
            SELECT LOWER(INDEX_NAME) AS idx_name,
                   MIN(LOWER(COLUMN_NAME)) AS col_name,
                   COUNT(*) AS col_count
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND LOWER(TABLE_NAME) = ?
              AND NON_UNIQUE = 0
              AND UPPER(INDEX_NAME) <> 'PRIMARY'
            GROUP BY LOWER(INDEX_NAME)
            """

        private val KNOWN_INDEX_COLUMNS: Map<Pair<String, String>, String> =
            mapOf(
                ("users" to "idx_users_email") to "email",
                ("users" to "uk_users_email") to "email",
                ("categories" to "uk_categories_slug") to "slug",
                ("categories" to "idx_categories_slug") to "slug",
                ("products" to "uk_products_sku") to "sku",
                ("products" to "idx_products_sku") to "sku",
                ("products" to "uk_products_slug") to "slug",
                ("products" to "idx_products_slug") to "slug",
            )

        private val ALLOWED_COLUMNS_BY_TABLE: Map<String, Set<String>> =
            mapOf(
                "users" to setOf("email"),
                "categories" to setOf("slug"),
                "products" to setOf("sku", "slug"),
            )
    }
}
