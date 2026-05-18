package com.github.logboard.log.service

import com.github.logboard.log.model.RawLog
import com.github.logboard.log.repository.ClickHouseLogRepository
import com.github.logboard.log.repository.RawLogRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogProcessingServiceTest {

    private val rawLogRepository = mock<RawLogRepository>()
    private val clickHouseLogRepository = mock<ClickHouseLogRepository>()
    private val elasticsearchOperations = mock<ElasticsearchOperations>()
    private val service = LogProcessingService(rawLogRepository, clickHouseLogRepository, elasticsearchOperations)

    private val projectId = UUID.randomUUID()
    private val ingestionId = UUID.randomUUID()

    private fun makeRawLog(level: String = "INFO", message: String = "msg", timestamp: Long = 1000L) = RawLog(
        projectId = projectId,
        ingestionId = ingestionId,
        level = level,
        message = message,
        timestamp = timestamp
    )

    @BeforeEach
    fun setUp() {
        clearInvocations(rawLogRepository, clickHouseLogRepository, elasticsearchOperations)
        whenever(rawLogRepository.saveAll<RawLog>(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `does nothing when no raw logs found`() {
        whenever(rawLogRepository.findByIngestionId(ingestionId)).thenReturn(emptyList())

        service.processAsync(ingestionId)

        verify(clickHouseLogRepository, never()).bulkInsert(any<List<com.github.logboard.log.model.LogDocument>>())
        verify(elasticsearchOperations, never()).save(any<Any>())
    }

    @Test
    fun `inserts all logs into ClickHouse`() {
        val logs = listOf(makeRawLog("INFO", "a"), makeRawLog("ERROR", "b"))
        whenever(rawLogRepository.findByIngestionId(ingestionId)).thenReturn(logs)

        service.processAsync(ingestionId)

        val captor = argumentCaptor<List<com.github.logboard.log.model.LogDocument>>()
        verify(clickHouseLogRepository).bulkInsert(captor.capture())
        val docs = captor.firstValue
        assert(docs.size == 2)
        assert(docs.any { it.level == "INFO" && it.message == "a" })
        assert(docs.any { it.level == "ERROR" && it.message == "b" })
    }

    @Test
    fun `saves each log to Elasticsearch`() {
        val logs = listOf(makeRawLog(), makeRawLog())
        whenever(rawLogRepository.findByIngestionId(ingestionId)).thenReturn(logs)

        service.processAsync(ingestionId)

        verify(elasticsearchOperations, org.mockito.kotlin.times(2)).save(any<Any>())
    }

    @Test
    fun `marks logs as processed after writing to stores`() {
        val log = makeRawLog()
        whenever(rawLogRepository.findByIngestionId(ingestionId)).thenReturn(listOf(log))

        service.processAsync(ingestionId)

        verify(rawLogRepository).saveAll(any<List<RawLog>>())
        assertNotNull(log.processedAt)
    }

    @Test
    fun `maps RawLog fields to LogDocument correctly`() {
        val log = makeRawLog(level = "WARN", message = "warning", timestamp = 9999L)
        whenever(rawLogRepository.findByIngestionId(ingestionId)).thenReturn(listOf(log))

        service.processAsync(ingestionId)

        val captor = argumentCaptor<List<com.github.logboard.log.model.LogDocument>>()
        verify(clickHouseLogRepository).bulkInsert(captor.capture())
        val doc = captor.firstValue.first()
        assert(doc.level == "WARN")
        assert(doc.message == "warning")
        assert(doc.timestamp == 9999L)
        assert(doc.projectId == projectId.toString())
        assert(doc.ingestionId == ingestionId.toString())
    }

    @Test
    fun `maps RawLog fields to LogDocumentEs correctly`() {
        val log = makeRawLog(level = "ERROR", message = "oops", timestamp = 42L)
        whenever(rawLogRepository.findByIngestionId(ingestionId)).thenReturn(listOf(log))

        service.processAsync(ingestionId)

        val captor = argumentCaptor<com.github.logboard.log.model.LogDocumentEs>()
        verify(elasticsearchOperations).save(captor.capture())
        val doc = captor.firstValue
        assert(doc.level == "ERROR")
        assert(doc.message == "oops")
        assert(doc.timestamp == 42L)
    }
}
