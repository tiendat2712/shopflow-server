package org.shopflow.backend.infrastructure.persistence.support

import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

class UniqueConstraintViolationTranslatorTest {
    @Test
    fun `resolves known MySQL 1062 and ANSI 23505 unique constraints across tables`() {
        val translator = UniqueConstraintViolationTranslator(emptyDataSource())

        assertEquals(
            "email",
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = "users.idx_users_email",
                    sqlMessage = "Duplicate entry 'a@b.com' for key 'users.idx_users_email'",
                ),
                expectedTable = "users",
            ),
        )

        assertEquals(
            "slug",
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = null,
                    sqlMessage = "Duplicate entry 'electronics' for key 'categories.uk_categories_slug'",
                ),
                expectedTable = "categories",
            ),
        )

        assertEquals(
            "sku",
            translator.resolveViolatedColumn(
                DuplicateKeyException(
                    "PreparedStatementCallback; Duplicate entry 'ITEM-1' for key 'products.uk_products_sku'",
                    SQLException(
                        "Duplicate entry 'ITEM-1' for key 'products.uk_products_sku'",
                        "23000",
                        1062,
                    ),
                ),
                expectedTable = "products",
            ),
        )

        assertEquals(
            "slug",
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 0,
                    sqlState = "23505",
                    constraintName = "uk_products_slug",
                    sqlMessage = "ERROR: duplicate key value violates unique constraint \"uk_products_slug\"",
                ),
                expectedTable = "products",
            ),
        )
    }

    @Test
    fun `resolves Hibernate-generated UK index names via single-column metadata lookup and rejects composite ones`() {
        val translator =
            UniqueConstraintViolationTranslator(
                stubMetadataDataSource(
                    listOf(
                        MetadataRow(indexName = "uk_generated_sku_hash", columnName = "sku", columnCount = 1),
                        MetadataRow(indexName = "uk_generated_slug_hash", columnName = "slug", columnCount = 1),
                        MetadataRow(indexName = "uk_composite_idx", columnName = "category_id", columnCount = 2),
                    ),
                ),
            )

        assertEquals(
            "sku",
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = "products.UK_generated_sku_hash",
                    sqlMessage = "Duplicate entry 'KEY1' for key 'products.UK_generated_sku_hash'",
                ),
                expectedTable = "products",
            ),
        )

        assertEquals(
            "slug",
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = "products.UK_generated_slug_hash",
                    sqlMessage = "Duplicate entry 'key1' for key 'products.UK_generated_slug_hash'",
                ),
                expectedTable = "products",
            ),
        )

        assertNull(
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = "products.UK_composite_idx",
                    sqlMessage = "Duplicate entry 'x' for key 'products.UK_composite_idx'",
                ),
                expectedTable = "products",
            ),
        )
    }

    @Test
    fun `returns null for non-unique DataIntegrityViolationExceptions PRIMARY keys and mismatched tables`() {
        val translator = UniqueConstraintViolationTranslator(emptyDataSource())

        // MySQL NOT NULL error (1048, SQLState 23000)
        assertNull(
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1048,
                    sqlState = "23000",
                    constraintName = null,
                    sqlMessage = "Column 'email' cannot be null",
                ),
                expectedTable = "users",
            ),
        )

        // MySQL foreign key error (1452, SQLState 23000)
        assertNull(
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1452,
                    sqlState = "23000",
                    constraintName = "fk_orders_user",
                    sqlMessage = "Cannot add or update a child row: a foreign key constraint fails",
                ),
                expectedTable = "products",
            ),
        )

        // MySQL data truncation / length error (1406, SQLState 22001)
        assertNull(
            translator.resolveViolatedColumn(
                DataIntegrityViolationException(
                    "Data truncation",
                    SQLException("Data too long for column 'email' at row 1", "22001", 1406),
                ),
                expectedTable = "users",
            ),
        )

        // PRIMARY key collision must not map to email/sku/slug
        assertNull(
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = "products.PRIMARY",
                    sqlMessage = "Duplicate entry 'id-1' for key 'products.PRIMARY'",
                ),
                expectedTable = "products",
            ),
        )

        // Table qualifier mismatch
        assertNull(
            translator.resolveViolatedColumn(
                mysqlConstraintException(
                    errorCode = 1062,
                    sqlState = "23000",
                    constraintName = "categories.uk_categories_slug",
                    sqlMessage = "Duplicate entry 'elec' for key 'categories.uk_categories_slug'",
                ),
                expectedTable = "products",
            ),
        )
    }

    private fun mysqlConstraintException(
        errorCode: Int,
        sqlState: String,
        constraintName: String?,
        sqlMessage: String,
    ): DataIntegrityViolationException {
        val sqlEx = SQLException(sqlMessage, sqlState, errorCode)
        val hibernateEx = ConstraintViolationException("could not execute statement", sqlEx, constraintName)
        return DataIntegrityViolationException("could not execute statement", hibernateEx)
    }

    private fun emptyDataSource(): DataSource = stubMetadataDataSource(emptyList())

    private fun stubMetadataDataSource(rows: List<MetadataRow>): DataSource {
        val resultSet = createResultSetProxy(rows)
        val statement =
            Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "executeQuery" -> resultSet
                    "close" -> null
                    "setString" -> null
                    else -> defaultValue(method.returnType)
                }
            } as PreparedStatement
        val connection =
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "prepareStatement" -> statement
                    "close" -> null
                    else -> defaultValue(method.returnType)
                }
            } as Connection
        return Proxy.newProxyInstance(
            DataSource::class.java.classLoader,
            arrayOf(DataSource::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getConnection" -> connection
                else -> defaultValue(method.returnType)
            }
        } as DataSource
    }

    private fun createResultSetProxy(rows: List<MetadataRow>): ResultSet {
        var cursor = -1
        return Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "next" -> {
                    cursor += 1
                    cursor < rows.size
                }
                "getString" -> {
                    val col = args?.firstOrNull() as? String
                    val row = rows[cursor]
                    when (col) {
                        "idx_name" -> row.indexName
                        "col_name" -> row.columnName
                        else -> null
                    }
                }
                "getInt" -> {
                    val col = args?.firstOrNull() as? String
                    val row = rows[cursor]
                    when (col) {
                        "col_count" -> row.columnCount
                        else -> 0
                    }
                }
                "close" -> null
                else -> defaultValue(method.returnType)
            }
        } as ResultSet
    }

    private fun defaultValue(returnType: Class<*>): Any? =
        when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            else -> null
        }

    private data class MetadataRow(
        val indexName: String,
        val columnName: String,
        val columnCount: Int,
    )
}
