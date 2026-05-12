package com.github.logboard.log.service

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogSearchServiceIntegrationTest {

    private val esContainer = ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0")
    ).apply {
        withEnv("xpack.security.enabled", "false")
        withEnv("discovery.type", "single-node")
    }

    private lateinit var operations: ElasticsearchOperations
    private lateinit var service: LogSearchService

    private val projectId = UUID.randomUUID().toString()
    private val otherProjectId = UUID.randomUUID().toString()
    private val defaultFrom = Instant.EPOCH
    private val defaultTo = Instant.parse("2099-01-01T00:00:00Z")

    @BeforeAll
    fun startContainer() {
        esContainer.start()
        val clientConfig = ClientConfiguration.builder()
            .connectedTo(esContainer.httpHostAddress)
            .build()
        val esClient = ElasticsearchClients.createImperative(clientConfig)
        val converter = MappingElasticsearchConverter(SimpleElasticsearchMappingContext()).also { it.afterPropertiesSet() }
        operations = ElasticsearchTemplate(esClient, converter)
        service = LogSearchService(operations)
        operations.indexOps(LogDocumentEs::class.java).createWithMapping()
    }

    @BeforeEach
    fun resetIndex() {
        operations.indexOps(IndexCoordinates.of("logs")).delete()
        operations.indexOps(LogDocumentEs::class.java).createWithMapping()
    }

    @AfterAll
    fun stopContainer() {
        esContainer.stop()
    }

    private fun indexDoc(doc: LogDocumentEs) {
        val query = IndexQueryBuilder().withId(doc.id).withObject(doc).build()
        operations.index(query, IndexCoordinates.of("logs"))
        operations.indexOps(IndexCoordinates.of("logs")).refresh()
    }

    @Test
    fun `should return empty result when no documents exist`() {
        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo))

        assertEquals(emptyList<Any>(), result.logs)
        assertEquals(0L, result.totalCount)
        assertNull(result.nextCursor)
    }

    @Test
    fun `should find documents by projectId`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "hello", 1000L))
        indexDoc(LogDocumentEs("id-2", otherProjectId, "ing-2", "INFO", "other", 2000L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo))

        assertEquals(1, result.logs.size)
        assertEquals("hello", result.logs.first().message)
    }

    @Test
    fun `should filter by level`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "info msg", 1000L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "ERROR", "error msg", 2000L))

        val result = service.search(
            LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo, level = listOf("ERROR"))
        )

        assertEquals(1, result.logs.size)
        assertEquals("ERROR", result.logs.first().level)
    }

    @Test
    fun `should search message case-insensitively`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "Connection timeout error", 1000L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "ERROR", "null pointer exception", 2000L))

        val result = service.search(
            LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo, message = "TIMEOUT")
        )

        assertEquals(1, result.logs.size)
        assertEquals("Connection timeout error", result.logs.first().message)
    }

    @Test
    fun `should filter by timestamp range`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "early", 100L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "INFO", "middle", 500L))
        indexDoc(LogDocumentEs("id-3", projectId, "ing-1", "INFO", "late", 900L))

        val result = service.search(
            LogSearchRequest(
                projectId = UUID.fromString(projectId),
                from = Instant.ofEpochMilli(200L),
                to = Instant.ofEpochMilli(800L)
            )
        )

        assertEquals(1, result.logs.size)
        assertEquals("middle", result.logs.first().message)
    }

    @Test
    fun `should return correct total when paginating`() {
        repeat(3) { i ->
            indexDoc(LogDocumentEs("id-$i", projectId, "ing-1", "INFO", "msg $i", i.toLong() * 1000))
        }

        val result = service.search(
            LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo, size = 1)
        )

        assertEquals(3L, result.totalCount)
        assertEquals(1, result.logs.size)
    }

    @Test
    fun `should sort results by timestamp descending`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "first", 1000L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "INFO", "third", 3000L))
        indexDoc(LogDocumentEs("id-3", projectId, "ing-1", "INFO", "second", 2000L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo))

        assertEquals(
            listOf(Instant.ofEpochMilli(3000L), Instant.ofEpochMilli(2000L), Instant.ofEpochMilli(1000L)),
            result.logs.map { it.timestamp }
        )
    }

    @Test
    fun `should paginate without overlapping results`() {
        repeat(5) { i ->
            indexDoc(LogDocumentEs("id-$i", projectId, "ing-1", "INFO", "msg $i", i.toLong() * 1000 + 1000))
        }

        val page1 = service.search(
            LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo, size = 2)
        )
        val page2 = service.search(
            LogSearchRequest(projectId = UUID.fromString(projectId), from = defaultFrom, to = defaultTo, size = 2, cursor = page1.nextCursor)
        )

        assertEquals(2, page1.logs.size)
        assertEquals(2, page2.logs.size)
        val timestamps1 = page1.logs.map { it.timestamp }.toSet()
        val timestamps2 = page2.logs.map { it.timestamp }.toSet()
        assertTrue(timestamps1.intersect(timestamps2).isEmpty())
        assertEquals(5L, page1.totalCount)
    }
}
