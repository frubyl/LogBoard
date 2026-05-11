package com.github.logboard.log.service

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.elasticsearch.core.query.Query
import java.util.UUID

class LogSearchServiceTest : DescribeSpec({

    val elasticsearchOperations = mock<ElasticsearchOperations>()
    val service = LogSearchService(elasticsearchOperations)

    val projectId = UUID.randomUUID()

    fun mockSearchReturning(docs: List<LogDocumentEs>, total: Long = docs.size.toLong()) {
        val hits = docs.map { doc ->
            val hit = mock<SearchHit<LogDocumentEs>>()
            whenever(hit.content).thenReturn(doc)
            hit
        }
        @Suppress("UNCHECKED_CAST")
        val searchHits = mock<SearchHits<LogDocumentEs>>()
        whenever(searchHits.totalHits).thenReturn(total)
        whenever(searchHits.searchHits).thenReturn(hits)
        whenever(elasticsearchOperations.search(any<Query>(), any<Class<*>>()))
            .thenReturn(searchHits as SearchHits<Any>)
    }

    beforeEach {
        clearInvocations(elasticsearchOperations)
        mockSearchReturning(emptyList())
    }

    describe("search") {

        it("возвращает пустой результат если нет логов") {
            val result = service.search(LogSearchRequest(projectId = projectId))

            result.items shouldBe emptyList()
            result.total shouldBe 0L
            result.page shouldBe 0
            result.size shouldBe 50
        }

        it("маппит LogDocumentEs в LogEntry корректно") {
            val doc = LogDocumentEs("id-1", projectId.toString(), "ing-1", "ERROR", "error occurred", 9999L)
            mockSearchReturning(listOf(doc), total = 1L)

            val result = service.search(LogSearchRequest(projectId = projectId))

            result.items.size shouldBe 1
            val entry = result.items.first()
            entry.id shouldBe "id-1"
            entry.ingestionId shouldBe "ing-1"
            entry.level shouldBe "ERROR"
            entry.message shouldBe "error occurred"
            entry.timestamp shouldBe 9999L
        }

        it("возвращает total из Elasticsearch") {
            mockSearchReturning(emptyList(), total = 42L)

            val result = service.search(LogSearchRequest(projectId = projectId))

            result.total shouldBe 42L
        }

        it("сохраняет page и size в ответе") {
            val result = service.search(LogSearchRequest(projectId = projectId, page = 2, size = 10))

            result.page shouldBe 2
            result.size shouldBe 10
        }

        it("делегирует запрос в ElasticsearchOperations") {
            service.search(LogSearchRequest(projectId = projectId, level = "WARN", message = "timeout"))

            verify(elasticsearchOperations).search(any<Query>(), any<Class<*>>())
        }

        it("возвращает несколько результатов с правильным маппингом") {
            val docs = listOf(
                LogDocumentEs("id-1", projectId.toString(), "ing-1", "INFO", "first", 1000L),
                LogDocumentEs("id-2", projectId.toString(), "ing-2", "ERROR", "second", 2000L)
            )
            mockSearchReturning(docs, total = 2L)

            val result = service.search(LogSearchRequest(projectId = projectId))

            result.items.size shouldBe 2
            result.items[0].id shouldBe "id-1"
            result.items[1].id shouldBe "id-2"
            result.total shouldBe 2L
        }
    }
})
