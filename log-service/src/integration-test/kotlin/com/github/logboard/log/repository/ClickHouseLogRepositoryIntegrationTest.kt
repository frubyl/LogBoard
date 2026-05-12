package com.github.logboard.log.repository

import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.model.LogDocument
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClickHouseLogRepositoryIntegrationTest {

    private val clickHouseContainer = GenericContainer<Nothing>(DockerImageName.parse("clickhouse/clickhouse-server:23.8-alpine"))
        .apply {
            withExposedPorts(8123)
            withEnv("CLICKHOUSE_DB", "default")
            withEnv("CLICKHOUSE_USER", "default")
            withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)))
        }

    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var repository: ClickHouseLogRepository

    private val projectId = "project-integration-test"

    @BeforeAll
    fun startContainer() {
        clickHouseContainer.start()
        val jdbcUrl = "jdbc:clickhouse://${clickHouseContainer.host}:${clickHouseContainer.getMappedPort(8123)}/default"
        dataSource = HikariDataSource().apply {
            this.jdbcUrl = jdbcUrl
            username = "default"
            password = ""
            driverClassName = "com.clickhouse.jdbc.ClickHouseDriver"
            maximumPoolSize = 2
        }
        jdbcTemplate = JdbcTemplate(dataSource)
        repository = ClickHouseLogRepository(jdbcTemplate)
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
    }

    @BeforeEach
    fun clearTable() {
        jdbcTemplate.execute("TRUNCATE TABLE logs")
    }

    @AfterAll
    fun stopContainer() {
        dataSource.close()
        clickHouseContainer.stop()
    }

    @Test
    fun `should insert documents without errors`() {
        val docs = listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "hello world", 1000L),
            LogDocument("id-2", projectId, "ing-1", "ERROR", "something failed", 2000L)
        )
        repository.bulkInsert(docs)

        val count = jdbcTemplate.queryForObject("SELECT count() FROM logs WHERE project_id = ?", Long::class.java, projectId)
        assertEquals(2L, count!!)
    }

    @Test
    fun `should do nothing when list is empty`() {
        repository.bulkInsert(emptyList())

        val count = jdbcTemplate.queryForObject("SELECT count() FROM logs", Long::class.java)
        assertEquals(0L, count!!)
    }

    @Test
    fun `should group logs into time buckets`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 0L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "msg", 30_000L),
            LogDocument("id-3", projectId, "ing-1", "ERROR", "msg", 30_000L),
            LogDocument("id-4", projectId, "ing-1", "INFO", "msg", 60_000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 90_000L, bucketMs = 60_000L, levels = null, message = null)

        val bucket0 = result.find { it.timestamp == Instant.ofEpochMilli(0L) }!!
        val bucket60 = result.find { it.timestamp == Instant.ofEpochMilli(60_000L) }!!
        assertEquals(3L, bucket0.totalCount)
        assertEquals(1L, bucket60.totalCount)
    }

    @Test
    fun `should aggregate error and warn counts per bucket`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 1000L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "msg", 2000L),
            LogDocument("id-3", projectId, "ing-1", "ERROR", "msg", 3000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, levels = null, message = null)

        assertEquals(1, result.size)
        assertEquals(3L, result.first().totalCount)
        assertEquals(1L, result.first().errorCount)
        assertEquals(0L, result.first().warnCount)
    }

    @Test
    fun `should filter by time range`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "early", 100L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "in range", 500L),
            LogDocument("id-3", projectId, "ing-1", "INFO", "late", 900L)
        ))

        val result = repository.timeline(projectId, from = 400L, to = 600L, bucketMs = 60_000L, levels = null, message = null)

        assertEquals(1L, result.sumOf { it.totalCount })
    }

    @Test
    fun `should filter by levels when levels are specified`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 1000L),
            LogDocument("id-2", projectId, "ing-1", "ERROR", "msg", 2000L),
            LogDocument("id-3", projectId, "ing-1", "WARN", "msg", 3000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, levels = listOf("ERROR"), message = null)

        assertEquals(1, result.size)
        assertEquals(1L, result.first().totalCount)
        assertEquals(1L, result.first().errorCount)
    }

    @Test
    fun `should not include logs from another project`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "mine", 1000L),
            LogDocument("id-2", "other-project", "ing-2", "INFO", "other", 1000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, levels = null, message = null)

        assertEquals(1L, result.sumOf { it.totalCount })
    }

    @Test
    fun `should return empty list when no logs exist`() {
        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, levels = null, message = null)

        assertEquals(emptyList<TimelineItem>(), result)
    }

    @Test
    fun `should return buckets sorted by timestamp ascending`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 120_000L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "msg", 0L),
            LogDocument("id-3", projectId, "ing-1", "INFO", "msg", 60_000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 180_000L, bucketMs = 60_000L, levels = null, message = null)

        val timestamps = result.map { it.timestamp }
        assertEquals(timestamps.sorted(), timestamps)
    }
}
