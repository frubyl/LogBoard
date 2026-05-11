package com.github.logboard.log.service

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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

class LogSearchServiceIntegrationTest : DescribeSpec({

    val esContainer = ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.0")
    ).apply {
        withEnv("xpack.security.enabled", "false")
        withEnv("discovery.type", "single-node")
        start()
    }

    val clientConfig = ClientConfiguration.builder()
        .connectedTo(esContainer.httpHostAddress)
        .build()

    val esClient = ElasticsearchClients.createImperative(clientConfig)
    val converter = MappingElasticsearchConverter(SimpleElasticsearchMappingContext()).also { it.afterPropertiesSet() }
    val operations: ElasticsearchOperations = ElasticsearchTemplate(esClient, converter)
    val service = LogSearchService(operations)

    val projectId = UUID.randomUUID().toString()
    val otherProjectId = UUID.randomUUID().toString()

    fun indexDoc(doc: LogDocumentEs) {
        val query = IndexQueryBuilder().withId(doc.id).withObject(doc).build()
        operations.index(query, IndexCoordinates.of("logs"))
        operations.indexOps(IndexCoordinates.of("logs")).refresh()
    }

    beforeSpec {
        operations.indexOps(LogDocumentEs::class.java).createWithMapping()
    }

    beforeEach {
        operations.indexOps(IndexCoordinates.of("logs")).delete()
        operations.indexOps(LogDocumentEs::class.java).createWithMapping()
    }

    afterSpec {
        esContainer.stop()
    }

    describe("search") {

        it("возвращает пустой результат при отсутствии документов") {
            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId)))

            result.items shouldBe emptyList()
            result.total shouldBe 0L
        }

        it("находит документы по projectId") {
            indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "hello", 1000L))
            indexDoc(LogDocumentEs("id-2", otherProjectId, "ing-2", "INFO", "other", 2000L))

            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId)))

            result.items.size shouldBe 1
            result.items.first().id shouldBe "id-1"
        }

        it("фильтрует по level") {
            indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "info msg", 1000L))
            indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "ERROR", "error msg", 2000L))

            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), level = "ERROR"))

            result.items.size shouldBe 1
            result.items.first().level shouldBe "ERROR"
        }

        it("выполняет регистронезависимый поиск по message") {
            indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "Connection timeout error", 1000L))
            indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "ERROR", "null pointer exception", 2000L))

            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), message = "TIMEOUT"))

            result.items.size shouldBe 1
            result.items.first().id shouldBe "id-1"
        }

        it("фильтрует по диапазону timestamp") {
            indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "early", 100L))
            indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "INFO", "middle", 500L))
            indexDoc(LogDocumentEs("id-3", projectId, "ing-1", "INFO", "late", 900L))

            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), from = 200L, to = 800L))

            result.items.size shouldBe 1
            result.items.first().id shouldBe "id-2"
        }

        it("возвращает total корректно при пагинации") {
            repeat(3) { i ->
                indexDoc(LogDocumentEs("id-$i", projectId, "ing-1", "INFO", "msg $i", i.toLong() * 1000))
            }

            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId), size = 1))

            result.total shouldBe 3L
            result.items.size shouldBe 1
        }

        it("сортирует результаты по убыванию timestamp") {
            indexDoc(LogDocumentEs("id-1", projectId, "ing-1", "INFO", "first", 1000L))
            indexDoc(LogDocumentEs("id-2", projectId, "ing-1", "INFO", "third", 3000L))
            indexDoc(LogDocumentEs("id-3", projectId, "ing-1", "INFO", "second", 2000L))

            val result = service.search(LogSearchRequest(projectId = UUID.fromString(projectId)))

            result.items.map { it.timestamp } shouldBe listOf(3000L, 2000L, 1000L)
        }

        it("применяет пагинацию без пересечений") {
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
})
