package com.github.logboard.log.service

import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.repository.ClickHouseLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimelineServiceTest {

    private val repository = mock<ClickHouseLogRepository>()
    private val service = TimelineService(repository)

    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearInvocations(repository)
        whenever(repository.timeline(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(emptyList())
    }

    @Test
    fun `delegates to repository with correct projectId, from, to`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(1000L)
        service.getTimeline(TimelineRequest(projectId, from = from, to = to))

        verify(repository).timeline(
            eq(projectId.toString()), eq(0L), eq(1000L), any(), anyOrNull(), anyOrNull()
        )
    }

    @Test
    fun `passes level list to repository`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(1000L)
        service.getTimeline(TimelineRequest(projectId, from, to, level = listOf("ERROR", "WARN")))

        verify(repository).timeline(any(), any(), any(), any(), eq(listOf("ERROR", "WARN")), anyOrNull())
    }

    @Test
    fun `passes message filter to repository`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(1000L)
        service.getTimeline(TimelineRequest(projectId, from, to, message = "timeout"))

        verify(repository).timeline(any(), any(), any(), any(), anyOrNull(), eq("timeout"))
    }

    @Test
    fun `returns empty list when repository returns no data`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(1000L)
        val result = service.getTimeline(TimelineRequest(projectId, from, to))

        assertEquals(emptyList<TimelineItem>(), result)
    }

    @Test
    fun `returns items from repository`() {
        val items = listOf(
            TimelineItem(Instant.ofEpochMilli(0L), 5L, 1L, 2L),
            TimelineItem(Instant.ofEpochMilli(60_000L), 3L, 0L, 1L)
        )
        whenever(repository.timeline(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(items)

        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(120_000L)
        val result = service.getTimeline(TimelineRequest(projectId, from, to))

        assertEquals(items, result)
    }

    @Test
    fun `calculates 1-minute buckets for range up to 1 hour`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(3_600_000L)
        service.getTimeline(TimelineRequest(projectId, from, to))

        verify(repository).timeline(any(), any(), any(), eq(60_000L), anyOrNull(), anyOrNull())
    }

    @Test
    fun `calculates 30-minute buckets for range up to 24 hours`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(86_400_000L)
        service.getTimeline(TimelineRequest(projectId, from, to))

        verify(repository).timeline(any(), any(), any(), eq(1_800_000L), anyOrNull(), anyOrNull())
    }

    @Test
    fun `calculates 1-hour buckets for range up to 7 days`() {
        val from = Instant.ofEpochMilli(0L)
        val to = Instant.ofEpochMilli(604_800_000L)
        service.getTimeline(TimelineRequest(projectId, from, to))

        verify(repository).timeline(any(), any(), any(), eq(3_600_000L), anyOrNull(), anyOrNull())
    }
}
