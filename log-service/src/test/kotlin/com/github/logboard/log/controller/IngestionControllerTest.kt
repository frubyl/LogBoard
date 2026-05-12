package com.github.logboard.log.controller

import com.github.logboard.log.dto.IngestEntry
import com.github.logboard.log.dto.IngestRequest
import com.github.logboard.log.service.IngestionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngestionControllerTest {

    private val ingestionService = mock<IngestionService>()
    private val controller = IngestionController(ingestionService)

    private val projectId = UUID.randomUUID()
    private val ingestionId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearInvocations(ingestionService)
        whenever(ingestionService.ingest(any(), any())).thenReturn(ingestionId)
    }

    @Test
    fun `returns 202 Accepted`() {
        val request = IngestRequest(listOf(IngestEntry("INFO", "hello", Instant.now())))

        val result = controller.ingest(projectId, request)

        assertEquals(HttpStatus.ACCEPTED, result.statusCode)
    }

    @Test
    fun `returns ingestionId in response body`() {
        val request = IngestRequest(listOf(IngestEntry("INFO", "hello", Instant.now())))

        val result = controller.ingest(projectId, request)

        assertEquals(ingestionId, result.body!!.ingestionId)
    }

    @Test
    fun `passes projectId and entries to ingestion service`() {
        val entry = IngestEntry("ERROR", "boom", Instant.ofEpochMilli(5000L))
        val request = IngestRequest(listOf(entry))

        controller.ingest(projectId, request)

        verify(ingestionService).ingest(eq(projectId), argThat { entries ->
            entries.size == 1 && entries[0].level == "ERROR" && entries[0].message == "boom"
        })
    }

    @Test
    fun `handles empty entries list`() {
        val request = IngestRequest(emptyList())

        val result = controller.ingest(projectId, request)

        assertEquals(HttpStatus.ACCEPTED, result.statusCode)
        verify(ingestionService).ingest(eq(projectId), eq(emptyList()))
    }
}
