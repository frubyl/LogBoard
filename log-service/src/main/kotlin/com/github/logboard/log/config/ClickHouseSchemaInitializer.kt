package com.github.logboard.log.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class ClickHouseSchemaInitializer(
    @Qualifier("clickHouseJdbcTemplate") private val jdbcTemplate: JdbcTemplate
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ClickHouseSchemaInitializer::class.java)
    }

    @PostConstruct
    fun initialize() {
        logger.info("Initializing ClickHouse schema")
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS logs (
                id String,
                project_id String,
                ingestion_id String,
                level LowCardinality(String),
                message String,
                timestamp Int64
            ) ENGINE = MergeTree()
            ORDER BY (project_id, timestamp)
        """.trimIndent())
        logger.info("ClickHouse schema initialized")
    }
}
