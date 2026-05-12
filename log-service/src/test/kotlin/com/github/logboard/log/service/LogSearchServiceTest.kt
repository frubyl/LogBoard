package com.github.logboard.log.service

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.elasticsearch.core.query.Query
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogSearchServiceTest {

    private val elasticsearchOperations = mock<ElasticsearchOperations>()
    private val service = LogSearchService(elasticsearchOperations)

    private val projectId = UUID.randomUUID()
    private val from = Instant.ofEpochMilli(0L)
    private val to = Instant.ofEpochMilli(10_000L)

    private fun mockSearchReturning(docs: List<LogDocumentEs>, total: Long = docs.size.toLong()) {
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

    @BeforeEach
    fun setUp() {
        clearInvocations(elasticsearchOperations)
        mockSearchReturning(emptyList())
    }

    @Test
    fun `returns empty result when there are no logs`() {
        val result = service.search(LogSearchRequest(projectId = projectId, from = from, to = to))

        assertEquals(emptyList<Any>(), result.logs)
        assertEquals(0L, result.totalCount)
        assertNull(result.nextCursor)
    }

    @Test
    fun `maps LogDocumentEs to LogEntry correctly`() {
        val doc = LogDocumentEs("id-1", projectId.toString(), "ing-1", "ERROR", "error occurred", 9999L)
        mockSearchReturning(listOf(doc), total = 1L)

        val result = service.search(LogSearchRequest(projectId = projectId, from = from, to = to))

        assertEquals(1, result.logs.size)
        val entry = result.logs.first()
        assertEquals("ERROR", entry.level)
        assertEquals("error occurred", entry.message)
        assertEquals(Instant.ofEpochMilli(9999L), entry.timestamp)
    }

    @Test
    fun `returns totalCount from Elasticsearch`() {
        mockSearchReturning(emptyList(), total = 42L)

        val result = service.search(LogSearchRequest(projectId = projectId, from = from, to = to))

        assertEquals(42L, result.totalCount)
    }

    @Test
    fun `sets nextCursor to last item timestamp when page is full`() {
        val docs = List(50) { i ->
            LogDocumentEs("id-$i", projectId.toString(), "ing-1", "INFO", "msg", i.toLong() * 1000)
        }
        mockSearchReturning(docs, total = 100L)

        val result = service.search(LogSearchRequest(projectId = projectId, from = from, to = to, size = 50))

        assertEquals(Instant.ofEpochMilli(49 * 1000L), result.nextCursor)
    }

    @Test
    fun `sets nextCursor to null when page is not full`() {
        val docs = listOf(LogDocumentEs("id-1", projectId.toString(), "ing-1", "INFO", "msg", 1000L))
        mockSearchReturning(docs, total = 1L)

        val result = service.search(LogSearchRequest(projectId = projectId, from = from, to = to, size = 50))

        assertNull(result.nextCursor)
    }

    @Test
    fun `delegates request to ElasticsearchOperations`() {
        service.search(LogSearchRequest(projectId = projectId, from = from, to = to, level = listOf("WARN"), message = "timeout"))

        verify(elasticsearchOperations).search(any<Query>(), any<Class<*>>())
    }

    @Test
    fun `returns multiple results with correct mapping`() {
        val docs = listOf(
            LogDocumentEs("id-1", projectId.toString(), "ing-1", "INFO", "first", 1000L),
            LogDocumentEs("id-2", projectId.toString(), "ing-2", "ERROR", "second", 2000L)
        )
        mockSearchReturning(docs, total = 2L)

        val result = service.search(LogSearchRequest(projectId = projectId, from = from, to = to))

        assertEquals(2, result.logs.size)
        assertEquals(2L, result.totalCount)
    }
}
