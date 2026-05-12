package com.github.logboard.log.repository

import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.model.LogDocument
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ClickHouseLogRepository(
    @Qualifier("clickHouseJdbcTemplate") private val jdbcTemplate: JdbcTemplate
) {

    fun bulkInsert(docs: List<LogDocument>) {
        if (docs.isEmpty()) return
        val sql = """
            INSERT INTO logs (id, project_id, ingestion_id, level, message, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        jdbcTemplate.batchUpdate(sql, docs.map { doc ->
            arrayOf<Any>(doc.id, doc.projectId, doc.ingestionId, doc.level, doc.message, doc.timestamp)
        })
    }

    fun timeline(
        projectId: String,
        from: Long,
        to: Long,
        bucketMs: Long,
        levels: List<String>?,
        message: String?
    ): List<TimelineItem> {
        val levelFilter = if (!levels.isNullOrEmpty()) {
            "AND level IN (${levels.joinToString { "?" }})"
        } else ""
        val messageFilter = if (message != null) "AND message ilike ?" else ""

        val sql = """
            SELECT
                intDiv(timestamp, ?) * ? AS bucket,
                count() AS totalCount,
                countIf(level = 'ERROR') AS errorCount,
                countIf(level = 'WARN') AS warnCount
            FROM logs
            WHERE project_id = ? AND timestamp >= ? AND timestamp <= ?
            $levelFilter $messageFilter
            GROUP BY bucket
            ORDER BY bucket ASC
        """.trimIndent()

        val params = buildList<Any> {
            add(bucketMs); add(bucketMs)
            add(projectId); add(from); add(to)
            levels?.forEach { add(it) }
            if (message != null) add("%$message%")
        }

        return jdbcTemplate.query(
            sql,
            { ps -> params.forEachIndexed { i, v -> ps.setObject(i + 1, v) } }
        ) { rs, _ ->
            TimelineItem(
                timestamp = Instant.ofEpochMilli(rs.getLong("bucket")),
                totalCount = rs.getLong("totalCount"),
                errorCount = rs.getLong("errorCount"),
                warnCount = rs.getLong("warnCount")
            )
        }
    }
}
