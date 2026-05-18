package com.github.logboard.log.service

import com.github.logboard.log.dto.IngestEntry
import com.github.logboard.log.model.RawLog
import com.github.logboard.log.repository.RawLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngestionServiceTest {

    private val rawLogRepository = mock<RawLogRepository>()
    private val logProcessingService = mock<LogProcessingService>()
    private val service = IngestionService(rawLogRepository, logProcessingService)

    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearInvocations(rawLogRepository, logProcessingService)
        whenever(rawLogRepository.saveAll<RawLog>(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `returns a non-null ingestionId`() {
        val entries = listOf(IngestEntry("INFO", "hello", Instant.ofEpochMilli(1000L)))

        val ingestionId = service.ingest(projectId, entries)

        assertNotNull(ingestionId)
    }

    @Test
    fun `saves one RawLog per entry`() {
        val entries = listOf(
            IngestEntry("INFO", "first", Instant.ofEpochMilli(1000L)),
            IngestEntry("ERROR", "second", Instant.ofEpochMilli(2000L))
        )

        service.ingest(projectId, entries)

        val captor = argumentCaptor<List<RawLog>>()
        verify(rawLogRepository).saveAll(captor.capture())
        assertEquals(2, captor.firstValue.size)
    }

    @Test
    fun `all saved logs share the same ingestionId`() {
        val entries = listOf(
            IngestEntry("INFO", "a", Instant.ofEpochMilli(1000L)),
            IngestEntry("WARN", "b", Instant.ofEpochMilli(2000L))
        )

        service.ingest(projectId, entries)

        val captor = argumentCaptor<List<RawLog>>()
        verify(rawLogRepository).saveAll(captor.capture())
        val ids = captor.firstValue.map { it.ingestionId }.distinct()
        assertEquals(1, ids.size)
    }

    @Test
    fun `maps entry fields to RawLog correctly`() {
        val timestamp = Instant.ofEpochMilli(5000L)
        val entries = listOf(IngestEntry("ERROR", "boom", timestamp))

        service.ingest(projectId, entries)

        val captor = argumentCaptor<List<RawLog>>()
        verify(rawLogRepository).saveAll(captor.capture())
        val log = captor.firstValue.first()
        assertEquals(projectId, log.projectId)
        assertEquals("ERROR", log.level)
        assertEquals("boom", log.message)
        assertEquals(5000L, log.timestamp)
    }

    @Test
    fun `triggers async processing after saving`() {
        val entries = listOf(IngestEntry("INFO", "msg", Instant.ofEpochMilli(1000L)))

        val ingestionId = service.ingest(projectId, entries)

        verify(logProcessingService).processAsync(ingestionId)
    }

    @Test
    fun `handles empty entry list`() {
        val ingestionId = service.ingest(projectId, emptyList())

        assertNotNull(ingestionId)
        verify(logProcessingService).processAsync(ingestionId)
    }
}
