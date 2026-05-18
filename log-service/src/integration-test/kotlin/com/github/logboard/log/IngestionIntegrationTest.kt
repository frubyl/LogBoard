package com.github.logboard.log

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.client.CoreServiceClient
import com.github.logboard.log.dto.IngestEntry
import com.github.logboard.log.dto.IngestRequest
import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.model.LogDocumentEs
import com.github.logboard.log.repository.LocalApiKeyRepository
import com.github.logboard.log.repository.RawLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"]
)
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IngestionIntegrationTest {

    @MockBean lateinit var coreServiceClient: CoreServiceClient
    @MockBean lateinit var localApiKeyRepository: LocalApiKeyRepository

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var rawLogRepository: RawLogRepository
    @Autowired lateinit var elasticsearchOperations: ElasticsearchOperations
    @Autowired @Qualifier("clickHouseJdbcTemplate") lateinit var clickHouseJdbcTemplate: JdbcTemplate

    companion object {
        private const val HMAC_SECRET = "test-hmac-secret-for-integration-tests"
        private const val VALID_RAW_KEY = "valid-api-key-for-ingestion-it"
        private const val EXPIRED_RAW_KEY = "expired-api-key-for-ingestion-it"

        val TEST_PROJECT_ID: UUID = UUID.randomUUID()
        val VALID_HASH: String = hmac(VALID_RAW_KEY)
        val EXPIRED_HASH: String = hmac(EXPIRED_RAW_KEY)

        @Container
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)

        @Container
        val clickHouse: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("clickhouse/clickhouse-server:23.8-alpine")
        )
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_DB", "default")
            .withEnv("CLICKHOUSE_USER", "default")
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)))
            .withReuse(true)

        @Container
        val elasticsearch: ElasticsearchContainer = ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0")
        ).apply {
            withEnv("xpack.security.enabled", "false")
            withEnv("discovery.type", "single-node")
            withReuse(true)
        }

        @Container
        val postgres: GenericContainer<*> = GenericContainer(DockerImageName.parse("postgres:15"))
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "logboard_test")
            .withEnv("POSTGRES_USER", "logboard")
            .withEnv("POSTGRES_PASSWORD", "logboard")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
            .withReuse(true)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val pgUrl = "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/logboard_test"
            registry.add("spring.datasource.url") { pgUrl }
            registry.add("spring.datasource.username") { "logboard" }
            registry.add("spring.datasource.password") { "logboard" }
            registry.add("spring.liquibase.url") { pgUrl }
            registry.add("spring.liquibase.user") { "logboard" }
            registry.add("spring.liquibase.password") { "logboard" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("clickhouse.url") {
                "jdbc:clickhouse://${clickHouse.host}:${clickHouse.getMappedPort(8123)}/default"
            }
            registry.add("spring.elasticsearch.uris") {
                "http://${elasticsearch.host}:${elasticsearch.getMappedPort(9200)}"
            }
        }

        fun hmac(data: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(), "HmacSHA256"))
            return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    @BeforeEach
    fun setUp() {
        rawLogRepository.deleteAll()

        val indexOps = elasticsearchOperations.indexOps(LogDocumentEs::class.java)
        if (!indexOps.exists()) indexOps.createWithMapping()

        whenever(localApiKeyRepository.findByKeyHash(VALID_HASH))
            .thenReturn(LocalApiKey(UUID.randomUUID(), TEST_PROJECT_ID, VALID_HASH, expiresAt = null))
        whenever(localApiKeyRepository.findByKeyHash(EXPIRED_HASH))
            .thenReturn(
                LocalApiKey(UUID.randomUUID(), TEST_PROJECT_ID, EXPIRED_HASH,
                    expiresAt = LocalDateTime.now().minusDays(1))
            )
    }

    private fun ingestBody(entries: List<IngestEntry>): String =
        objectMapper.writeValueAsString(IngestRequest(entries))

    private fun awaitProcessed(ingestionId: UUID, timeoutMs: Long = 20_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (rawLogRepository.findByIngestionId(ingestionId).all { it.processedAt != null }) return
            Thread.sleep(200)
        }
        error("Ingestion $ingestionId was not fully processed within ${timeoutMs}ms")
    }

    private fun countInEsByIngestionId(ingestionId: UUID): Long {
        elasticsearchOperations.indexOps(LogDocumentEs::class.java).refresh()
        val nativeQuery = NativeQuery.builder()
            .withQuery(Query.of { q ->
                q.term { t -> t.field("ingestionId").value(ingestionId.toString()) }
            })
            .build()
        return elasticsearchOperations.search(nativeQuery, LogDocumentEs::class.java).totalHits
    }

    private fun countInClickHouseByIngestionId(ingestionId: UUID): Long =
        clickHouseJdbcTemplate.queryForObject(
            "SELECT count() FROM logs WHERE ingestion_id = ?",
            Long::class.java, ingestionId.toString()
        ) ?: 0L

    // ─── Auth ────────────────────────────────────────────────────────────────

    @Test
    fun `should return 401 when X-Api-Key header is missing`() {
        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(IngestEntry("INFO", "test", Instant.now()))))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 401 when API key is not found`() {
        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(IngestEntry("INFO", "test", Instant.now()))))
                .header("X-Api-Key", "unknown-key-${UUID.randomUUID()}")
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 401 when API key is expired`() {
        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(IngestEntry("INFO", "test", Instant.now()))))
                .header("X-Api-Key", EXPIRED_RAW_KEY)
        ).andExpect(status().isUnauthorized())
    }

    // ─── Happy path ──────────────────────────────────────────────────────────

    @Test
    fun `should return 202 Accepted with ingestionId when API key is valid`() {
        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(IngestEntry("INFO", "hello", Instant.now()))))
                .header("X-Api-Key", VALID_RAW_KEY)
        )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.ingestionId").exists())
    }

    @Test
    fun `should handle empty entries list`() {
        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(emptyList()))
                .header("X-Api-Key", VALID_RAW_KEY)
        )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.ingestionId").exists())
    }

    // ─── PostgreSQL staging ──────────────────────────────────────────────────

    @Test
    fun `should save all entries to raw_logs before returning response`() {
        val entries = listOf(
            IngestEntry("INFO", "first", Instant.ofEpochMilli(1000L)),
            IngestEntry("ERROR", "second", Instant.ofEpochMilli(2000L)),
            IngestEntry("WARN", "third", Instant.ofEpochMilli(3000L))
        )

        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(entries))
                .header("X-Api-Key", VALID_RAW_KEY)
        ).andExpect(status().isAccepted())

        val saved = rawLogRepository.findAll()
        assertEquals(3, saved.size)
        assertTrue(saved.any { it.level == "INFO"  && it.message == "first" })
        assertTrue(saved.any { it.level == "ERROR" && it.message == "second" })
        assertTrue(saved.any { it.level == "WARN"  && it.message == "third" })
    }

    @Test
    fun `should assign the same ingestionId to all entries in a batch`() {
        val result = mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(
                    IngestEntry("INFO", "a", Instant.now()),
                    IngestEntry("ERROR", "b", Instant.now())
                )))
                .header("X-Api-Key", VALID_RAW_KEY)
        ).andExpect(status().isAccepted()).andReturn()

        val ingestionId = UUID.fromString(
            objectMapper.readTree(result.response.contentAsString)["ingestionId"].asText()
        )
        val saved = rawLogRepository.findByIngestionId(ingestionId)
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.projectId == TEST_PROJECT_ID })
    }

    @Test
    fun `should store correct projectId from API key in raw_logs`() {
        mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(IngestEntry("INFO", "msg", Instant.now()))))
                .header("X-Api-Key", VALID_RAW_KEY)
        ).andExpect(status().isAccepted())

        val saved = rawLogRepository.findAll()
        assertEquals(1, saved.size)
        assertEquals(TEST_PROJECT_ID, saved.first().projectId)
    }

    // ─── Async replication ───────────────────────────────────────────────────

    @Test
    fun `should replicate logs to Elasticsearch after async processing`() {
        val result = mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(
                    IngestEntry("INFO", "es-msg-1", Instant.now()),
                    IngestEntry("ERROR", "es-msg-2", Instant.now())
                )))
                .header("X-Api-Key", VALID_RAW_KEY)
        ).andExpect(status().isAccepted()).andReturn()

        val ingestionId = UUID.fromString(
            objectMapper.readTree(result.response.contentAsString)["ingestionId"].asText()
        )

        awaitProcessed(ingestionId)

        assertEquals(2L, countInEsByIngestionId(ingestionId))
    }

    @Test
    fun `should replicate logs to ClickHouse after async processing`() {
        val result = mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(
                    IngestEntry("WARN", "ch-msg-1", Instant.now()),
                    IngestEntry("ERROR", "ch-msg-2", Instant.now())
                )))
                .header("X-Api-Key", VALID_RAW_KEY)
        ).andExpect(status().isAccepted()).andReturn()

        val ingestionId = UUID.fromString(
            objectMapper.readTree(result.response.contentAsString)["ingestionId"].asText()
        )

        awaitProcessed(ingestionId)

        assertEquals(2L, countInClickHouseByIngestionId(ingestionId))
    }

    @Test
    fun `should mark raw_logs as processed after replication`() {
        val result = mockMvc.perform(
            post("/logs/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ingestBody(listOf(IngestEntry("INFO", "processed-check", Instant.now()))))
                .header("X-Api-Key", VALID_RAW_KEY)
        ).andExpect(status().isAccepted()).andReturn()

        val ingestionId = UUID.fromString(
            objectMapper.readTree(result.response.contentAsString)["ingestionId"].asText()
        )

        awaitProcessed(ingestionId)

        val logs = rawLogRepository.findByIngestionId(ingestionId)
        assertEquals(1, logs.size)
        assertNotNull(logs.first().processedAt)
    }
}
