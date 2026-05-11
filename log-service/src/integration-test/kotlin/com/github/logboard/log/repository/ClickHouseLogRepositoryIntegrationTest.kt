package com.github.logboard.log.repository

import com.github.logboard.log.model.LogDocument
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClickHouseLogRepositoryIntegrationTest {

    private val clickHouseContainer = GenericContainer<Nothing>(DockerImageName.parse("clickhouse/clickhouse-server:23.8-alpine"))
        .apply {
            withExposedPorts(8123)
            withEnv("CLICKHOUSE_DB", "default")
            withEnv("CLICKHOUSE_USER", "default")
            withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
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

    // bulkInsert

    @Test
    fun `вставляет документы без ошибок`() {
        val docs = listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "hello world", 1000L),
            LogDocument("id-2", projectId, "ing-1", "ERROR", "something failed", 2000L)
        )
        repository.bulkInsert(docs)

        val count = jdbcTemplate.queryForObject("SELECT count() FROM logs WHERE project_id = ?", Long::class.java, projectId)
        count shouldBe 2L
    }

    @Test
    fun `ничего не делает при пустом списке`() {
        repository.bulkInsert(emptyList())

        val count = jdbcTemplate.queryForObject("SELECT count() FROM logs", Long::class.java)
        count shouldBe 0L
    }

    // timeline

    @Test
    fun `группирует логи по бакетам и уровням`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 0L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "msg", 30_000L),
            LogDocument("id-3", projectId, "ing-1", "ERROR", "msg", 30_000L),
            LogDocument("id-4", projectId, "ing-1", "INFO", "msg", 60_000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 90_000L, bucketMs = 60_000L, level = null)

        val bucket0 = result.filter { it.bucket == 0L }
        val bucket60 = result.filter { it.bucket == 60_000L }
        bucket0.sumOf { it.count } shouldBe 3L
        bucket60.sumOf { it.count } shouldBe 1L
    }

    @Test
    fun `агрегирует по уровню в пределах одного бакета`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 1000L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "msg", 2000L),
            LogDocument("id-3", projectId, "ing-1", "ERROR", "msg", 3000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, level = null)

        result.first { it.level == "INFO" }.count shouldBe 2L
        result.first { it.level == "ERROR" }.count shouldBe 1L
    }

    @Test
    fun `фильтрует по временному диапазону`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "early", 100L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "in range", 500L),
            LogDocument("id-3", projectId, "ing-1", "INFO", "late", 900L)
        ))

        val result = repository.timeline(projectId, from = 400L, to = 600L, bucketMs = 60_000L, level = null)

        result.sumOf { it.count } shouldBe 1L
    }

    @Test
    fun `фильтрует по level при указании уровня`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 1000L),
            LogDocument("id-2", projectId, "ing-1", "ERROR", "msg", 2000L),
            LogDocument("id-3", projectId, "ing-1", "WARN", "msg", 3000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, level = "ERROR")

        result.size shouldBe 1
        result.first().level shouldBe "ERROR"
        result.first().count shouldBe 1L
    }

    @Test
    fun `не включает логи другого проекта`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "mine", 1000L),
            LogDocument("id-2", "other-project", "ing-2", "INFO", "other", 1000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, level = null)

        result.sumOf { it.count } shouldBe 1L
    }

    @Test
    fun `возвращает пустой список если нет логов`() {
        val result = repository.timeline(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L, level = null)

        result shouldBe emptyList()
    }

    @Test
    fun `бакеты отсортированы по возрастанию`() {
        repository.bulkInsert(listOf(
            LogDocument("id-1", projectId, "ing-1", "INFO", "msg", 120_000L),
            LogDocument("id-2", projectId, "ing-1", "INFO", "msg", 0L),
            LogDocument("id-3", projectId, "ing-1", "INFO", "msg", 60_000L)
        ))

        val result = repository.timeline(projectId, from = 0L, to = 180_000L, bucketMs = 60_000L, level = null)

        val buckets = result.map { it.bucket }
        buckets shouldBe buckets.sorted()
    }
}
