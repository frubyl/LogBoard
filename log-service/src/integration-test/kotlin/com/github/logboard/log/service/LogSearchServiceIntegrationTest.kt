package com.github.logboard.log.service

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
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
    fun `возвращает пустой результат при отсутствии документов`() {
        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId)))

        result.items shouldBe emptyList()
        result.total shouldBe 0L
    }

    @Test
    fun `находит документы по projectId`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "hello", 1000L))
        indexDoc(LogDocumentEs("id-2", otherProjectId, "ing-2", "INFO", "other", 2000L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId)))

        result.items.size shouldBe 1
        result.items.first().id shouldBe "id-1"
    }

    @Test
    fun `фильтрует по level`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "info msg", 1000L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "ERROR", "error msg", 2000L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), level = "ERROR"))

        result.items.size shouldBe 1
        result.items.first().level shouldBe "ERROR"
    }

    @Test
    fun `выполняет регистронезависимый поиск по message`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "Connection timeout error", 1000L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "ERROR", "null pointer exception", 2000L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), message = "TIMEOUT"))

        result.items.size shouldBe 1
        result.items.first().id shouldBe "id-1"
    }

    @Test
    fun `фильтрует по диапазону timestamp`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "early", 100L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "INFO", "middle", 500L))
        indexDoc(LogDocumentEs("id-3", projectId, "ing-1", "INFO", "late", 900L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), from = 200L, to = 800L))

        result.items.size shouldBe 1
        result.items.first().id shouldBe "id-2"
    }

    @Test
    fun `возвращает total корректно при пагинации`() {
        repeat(3) { i ->
            indexDoc(LogDocumentEs("id-$i", projectId, "ing-1", "INFO", "msg $i", i.toLong() * 1000))
        }

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), size = 1))

        result.total shouldBe 3L
        result.items.size shouldBe 1
    }

    @Test
    fun `сортирует результаты по убыванию timestamp`() {
        indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "first", 1000L))
        indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "INFO", "third", 3000L))
        indexDoc(LogDocumentEs("id-3", projectId, "ing-1", "INFO", "second", 2000L))

        val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId)))

        result.items.map { it.timestamp } shouldBe listOf(3000L, 2000L, 1000L)
    }

    @Test
    fun `применяет пагинацию без пересечений`() {
        repeat(5) { i ->
            indexDoc(LogDocumentEs("id-$i", projectId, "ing-1", "INFO", "msg $i", i.toLong() * 1000))
        }

        val page0 = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), page = 0, size = 2))
        val page1 = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), page = 1, size = 2))

        page0.items.size shouldBe 2
        page1.items.size shouldBe 2
        page0.items.map { it.id }.intersect(page1.items.map { it.id }.toSet()).size shouldBe 0
        page0.total shouldBe 5L
    }
}
