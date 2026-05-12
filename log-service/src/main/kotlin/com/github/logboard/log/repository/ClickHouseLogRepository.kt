package com.github.logboard.log.repository

import com.github.logboard.log.dto.TimelineBucket
import com.github.logboard.log.model.LogDocument
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

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
        val batchArgs = docs.map { doc ->
            arrayOf<Any>(doc.id, doc.projectId, doc.ingestionId, doc.level, doc.message, doc.timestamp)
        }
        jdbcTemplate.batchUpdate(sql, batchArgs)
    }

    fun timeline(
        projectId: String,
        from: Long,
        to: Long,
        bucketMs: Long,
        level: String?
    ): List<TimelineBucket> {
        val levelFilter = if (level != null) "AND level = ?" else ""
        val sql = """
            SELECT intDiv(timestamp, ?) * ? AS bucket, level, count() AS cnt
            FROM logs
            WHERE project_id = ? AND timestamp >= ? AND timestamp <= ? $levelFilter
            GROUP BY bucket, level
            ORDER BY bucket ASC
        """.trimIndent()

        val params = buildList<Any> {
            add(bucketMs)
            add(bucketMs)
            add(projectId)
            add(from)
            add(to)
            if (level != null) add(level)
        }

        return jdbcTemplate.query(
            sql,
            { ps -> params.forEachIndexed { i, v -> ps.setObject(i + 1, v) } }
        ) { rs, _ ->
            TimelineBucket(
                bucket = rs.getLong("bucket"),
                level = rs.getString("level"),
                count = rs.getLong("cnt")
            )
        }
    }
}
