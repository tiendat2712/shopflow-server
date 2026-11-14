package org.shopflow.backend.infrastructure.dataset

import org.shopflow.backend.domain.service.dataset.DatasetBatchImportPort
import org.shopflow.backend.domain.service.dataset.OrderItemSeedRow
import org.shopflow.backend.domain.service.dataset.OrderSeedRow
import org.shopflow.backend.domain.service.dataset.ReviewSeedRow
import org.shopflow.backend.domain.service.dataset.UserEventSeedRow
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

@Component
class JdbcDatasetBatchImportAdapter(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : DatasetBatchImportPort {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun insertOrdersIgnore(
        rows: List<OrderSeedRow>,
        auditedAt: Instant,
    ): Int {
        val auditTs = Timestamp.from(auditedAt)
        return executeBatchInsertIgnore(
            tableName = TABLE_ORDERS,
            rows = rows,
            sql = SQL_INSERT_ORDER_IGNORE,
            idSelector = { it.id },
        ) { ps, row, utcCal ->
            ps.setString(1, row.id)
            ps.setString(2, row.userId)
            ps.setTimestamp(3, Timestamp.from(row.orderDate), utcCal)
            ps.setString(4, row.orderStatus)
            ps.setBigDecimal(5, row.totalAmount)
            ps.setTimestamp(6, auditTs, utcCal)
            ps.setTimestamp(7, auditTs, utcCal)
        }
    }

    override fun insertOrderItemsIgnore(
        rows: List<OrderItemSeedRow>,
        auditedAt: Instant,
    ): Int {
        val auditTs = Timestamp.from(auditedAt)
        return executeBatchInsertIgnore(
            tableName = TABLE_ORDER_ITEMS,
            rows = rows,
            sql = SQL_INSERT_ORDER_ITEM_IGNORE,
            idSelector = { it.id },
        ) { ps, row, utcCal ->
            ps.setString(1, row.id)
            ps.setString(2, row.orderId)
            ps.setString(3, row.productId)
            ps.setString(4, row.userId)
            ps.setInt(5, row.quantity)
            ps.setBigDecimal(6, row.itemPrice)
            ps.setBigDecimal(7, row.itemTotal)
            ps.setTimestamp(8, auditTs, utcCal)
            ps.setTimestamp(9, auditTs, utcCal)
        }
    }

    override fun insertReviewsIgnore(
        rows: List<ReviewSeedRow>,
        auditedAt: Instant,
    ): Int {
        val auditTs = Timestamp.from(auditedAt)
        return executeBatchInsertIgnore(
            tableName = TABLE_REVIEWS,
            rows = rows,
            sql = SQL_INSERT_REVIEW_IGNORE,
            idSelector = { it.id },
        ) { ps, row, utcCal ->
            ps.setString(1, row.id)
            ps.setString(2, row.orderId)
            ps.setString(3, row.productId)
            ps.setString(4, row.userId)
            ps.setInt(5, row.rating)
            if (row.reviewText == null) {
                ps.setNull(6, Types.LONGVARCHAR)
            } else {
                ps.setString(6, row.reviewText)
            }
            ps.setTimestamp(7, Timestamp.from(row.reviewDate), utcCal)
            ps.setTimestamp(8, auditTs, utcCal)
            ps.setTimestamp(9, auditTs, utcCal)
        }
    }

    override fun syncProductReviewCounts(): Int =
        transactionTemplate.execute {
            jdbcTemplate.update(SQL_SYNC_PRODUCT_REVIEW_COUNTS)
        }

    override fun insertUserEventsIgnore(
        rows: List<UserEventSeedRow>,
        auditedAt: Instant,
    ): Int {
        val auditTs = Timestamp.from(auditedAt)
        return executeBatchInsertIgnore(
            tableName = TABLE_USER_EVENTS,
            rows = rows,
            sql = SQL_INSERT_USER_EVENT_IGNORE,
            idSelector = { it.id },
        ) { ps, row, utcCal ->
            ps.setString(1, row.id)
            ps.setString(2, row.userId)
            ps.setString(3, row.productId)
            ps.setString(4, row.eventType)
            ps.setTimestamp(5, Timestamp.from(row.eventTimestamp), utcCal)
            ps.setTimestamp(6, auditTs, utcCal)
            ps.setTimestamp(7, auditTs, utcCal)
        }
    }

    private fun <T> executeBatchInsertIgnore(
        tableName: String,
        rows: List<T>,
        sql: String,
        idSelector: (T) -> String,
        binder: (PreparedStatement, T, Calendar) -> Unit,
    ): Int {
        if (rows.isEmpty()) {
            return 0
        }
        require(tableName in ALLOWED_TABLES) { "Unsupported dataset table: $tableName" }

        return transactionTemplate.execute {
            val uniqueIds = rows.mapTo(linkedSetOf(), idSelector).toList()
            val existingCount = countExistingIds(tableName, uniqueIds)
            val utcCalendar = Calendar.getInstance(UTC_TIME_ZONE)

            val updateCounts =
                jdbcTemplate.batchUpdate(
                    sql,
                    object : BatchPreparedStatementSetter {
                        override fun setValues(
                            ps: PreparedStatement,
                            i: Int,
                        ) {
                            binder(ps, rows[i], utcCalendar)
                        }

                        override fun getBatchSize(): Int = rows.size
                    },
                )

            if (updateCounts.all { it >= 0 }) {
                updateCounts.sum()
            } else {
                // MySQL rewriteBatchedStatements=true reports SUCCESS_NO_INFO (-2) per row.
                (uniqueIds.size - existingCount).coerceAtLeast(0)
            }
        }
    }

    private fun countExistingIds(
        tableName: String,
        ids: List<String>,
    ): Int {
        if (ids.isEmpty()) {
            return 0
        }
        var existingTotal = 0
        ids.chunked(ID_LOOKUP_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(", ") { "?" }
            val querySql = "SELECT COUNT(*) FROM $tableName WHERE id IN ($placeholders)"
            val chunkCount = jdbcTemplate.queryForObject(querySql, Int::class.java, *chunk.toTypedArray()) ?: 0
            existingTotal += chunkCount
        }
        return existingTotal
    }

    private companion object {
        private const val TABLE_ORDERS = "orders"
        private const val TABLE_ORDER_ITEMS = "order_items"
        private const val TABLE_REVIEWS = "reviews"
        private const val TABLE_USER_EVENTS = "user_events"
        private val ALLOWED_TABLES = setOf(TABLE_ORDERS, TABLE_ORDER_ITEMS, TABLE_REVIEWS, TABLE_USER_EVENTS)
        private const val ID_LOOKUP_CHUNK_SIZE = 1000
        private val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

        private const val SQL_INSERT_ORDER_IGNORE =
            "INSERT IGNORE INTO orders " +
                "(id, user_id, order_date, order_status, total_amount, " +
                "shipping_address, shipping_city, payment_method, payment_status, version, " +
                "created_at, updated_at, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, 0, ?, ?, 'SYSTEM', 'SYSTEM')"

        private const val SQL_INSERT_ORDER_ITEM_IGNORE =
            "INSERT IGNORE INTO order_items " +
                "(id, order_id, product_id, user_id, quantity, item_price, item_total, " +
                "created_at, updated_at, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYSTEM', 'SYSTEM')"

        private const val SQL_INSERT_REVIEW_IGNORE =
            "INSERT IGNORE INTO reviews " +
                "(id, order_id, product_id, user_id, rating, review_text, review_date, " +
                "created_at, updated_at, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYSTEM', 'SYSTEM')"

        private const val SQL_INSERT_USER_EVENT_IGNORE =
            "INSERT IGNORE INTO user_events " +
                "(id, user_id, product_id, event_type, event_timestamp, " +
                "created_at, updated_at, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'SYSTEM', 'SYSTEM')"

        private const val SQL_SYNC_PRODUCT_REVIEW_COUNTS =
            "UPDATE products p " +
                "LEFT JOIN (SELECT product_id, COUNT(*) AS cnt FROM reviews GROUP BY product_id) r " +
                "ON p.id = r.product_id " +
                "SET p.review_count = COALESCE(r.cnt, 0) " +
                "WHERE p.review_count <> COALESCE(r.cnt, 0)"
    }
}
