package com.github.logboard.log.controller

import com.github.logboard.log.dto.LogEntry
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.exception.ForbiddenException
import com.github.logboard.log.security.AuthenticatedUser
import com.github.logboard.log.service.LogSearchService
import com.github.logboard.log.service.MembershipService
import com.github.logboard.log.service.TimelineService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LogControllerTest {

    private val membershipService = mock<MembershipService>()
    private val logSearchService = mock<LogSearchService>()
    private val timelineService = mock<TimelineService>()
    private val controller = LogController(membershipService, logSearchService, timelineService)

    private val projectId = UUID.randomUUID()
    private val principal = AuthenticatedUser(userId = 1L, token = "test-token")
    private val from = Instant.ofEpochMilli(0L)
    private val to = Instant.ofEpochMilli(10_000L)
    private val emptySearchResponse = LogSearchResponse(logs = emptyList(), totalCount = 0L)

    @BeforeEach
    fun setUp() {
        reset(membershipService, logSearchService, timelineService)
    }

    @Test
    fun `search returns 200 for project member`() {
        whenever(membershipService.getMembership(1L, projectId, "test-token")).thenReturn("OWNER")
        whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

        val result = controller.search(principal, LogSearchRequest(projectId, from, to))

        assertEquals(HttpStatus.OK, result.statusCode)
        verify(membershipService).getMembership(1L, projectId, "test-token")
        verify(logSearchService).search(any())
    }

    @Test
    fun `search throws ForbiddenException when user is not a member`() {
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn(null)

        assertThrows(ForbiddenException::class.java) {
            controller.search(principal, LogSearchRequest(projectId, from, to))
        }

        verify(logSearchService, never()).search(any())
    }

    @Test
    fun `search allows access for READER role`() {
        whenever(membershipService.getMembership(1L, projectId, "test-token")).thenReturn("READER")
        whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

        val result = controller.search(principal, LogSearchRequest(projectId, from, to))

        assertEquals(HttpStatus.OK, result.statusCode)
    }

    @Test
    fun `search passes all filters to search service`() {
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn("ADMIN")
        whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

        controller.search(
            principal,
            LogSearchRequest(projectId, from, to, level = listOf("ERROR"), message = "timeout", size = 20)
        )

        verify(logSearchService).search(argThat { req ->
            req.projectId == projectId &&
                req.level == listOf("ERROR") &&
                req.message == "timeout" &&
                req.size == 20
        })
    }

    @Test
    fun `search caps page size at 500`() {
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn("OWNER")
        whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

        controller.search(principal, LogSearchRequest(projectId, from, to, size = 9999))

        verify(logSearchService).search(argThat { req -> req.size == 500 })
    }

    @Test
    fun `search returns response from search service`() {
        val entries = listOf(LogEntry(level = "INFO", message = "msg", timestamp = Instant.ofEpochMilli(1000L)))
        val response = LogSearchResponse(logs = entries, totalCount = 1L)
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn("OWNER")
        whenever(logSearchService.search(any())).thenReturn(response)

        val result = controller.search(principal, LogSearchRequest(projectId, from, to))

        assertEquals(1, result.body!!.logs.size)
        assertEquals(1L, result.body!!.totalCount)
    }

    @Test
    fun `timeline returns 200 for project member`() {
        whenever(membershipService.getMembership(1L, projectId, "test-token")).thenReturn("OWNER")
        whenever(timelineService.getTimeline(any())).thenReturn(emptyList())

        val result = controller.timeline(principal, TimelineRequest(projectId, from, to))

        assertEquals(HttpStatus.OK, result.statusCode)
    }

    @Test
    fun `timeline throws ForbiddenException when user is not a member`() {
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn(null)

        assertThrows(ForbiddenException::class.java) {
            controller.timeline(principal, TimelineRequest(projectId, from, to))
        }

        verify(timelineService, never()).getTimeline(any())
    }

    @Test
    fun `timeline passes parameters to timeline service`() {
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn("ADMIN")
        whenever(timelineService.getTimeline(any())).thenReturn(emptyList())

        controller.timeline(principal, TimelineRequest(projectId, from, to, level = listOf("ERROR"), message = "oops"))

        verify(timelineService).getTimeline(argThat { req ->
            req.projectId == projectId &&
                req.from == from &&
                req.to == to &&
                req.level == listOf("ERROR") &&
                req.message == "oops"
        })
    }

    @Test
    fun `timeline returns items from timeline service`() {
        val items = listOf(
            TimelineItem(Instant.ofEpochMilli(0L), 5L, 1L, 2L),
            TimelineItem(Instant.ofEpochMilli(60_000L), 3L, 0L, 1L)
        )
        whenever(membershipService.getMembership(any(), any(), any())).thenReturn("OWNER")
        whenever(timelineService.getTimeline(any())).thenReturn(items)

        val result = controller.timeline(principal, TimelineRequest(projectId, from, to))

        assertEquals(2, result.body!!.size)
    }
}
