package com.github.logboard.log.controller

import com.github.logboard.log.dto.LogEntry
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.dto.TimelineBucket
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.dto.TimelineResponse
import com.github.logboard.log.exception.ForbiddenException
import com.github.logboard.log.security.AuthenticatedUser
import com.github.logboard.log.service.LogSearchService
import com.github.logboard.log.service.MembershipService
import com.github.logboard.log.service.TimelineService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.UUID

class LogControllerTest : DescribeSpec({

    val membershipService = mock<MembershipService>()
    val logSearchService = mock<LogSearchService>()
    val timelineService = mock<TimelineService>()
    val controller = LogController(membershipService, logSearchService, timelineService)

    val projectId = UUID.randomUUID()
    val principal = AuthenticatedUser(userId = 1L, token = "test-token")
    val emptySearchResponse = LogSearchResponse(items = emptyList(), total = 0L, page = 0, size = 50)
    val emptyTimelineResponse = TimelineResponse(buckets = emptyList())

    beforeEach {
        reset(membershipService, logSearchService, timelineService)
    }

    describe("search") {

        it("возвращает 200 для члена проекта") {
            whenever(membershipService.getMembership(1L, projectId, "test-token")).thenReturn("OWNER")
            whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

            val result = controller.search(principal, LogSearchRequest(projectId))

            result.statusCode shouldBe HttpStatus.OK
            verify(membershipService).getMembership(1L, projectId, "test-token")
            verify(logSearchService).search(any())
        }

        it("бросает ForbiddenException при отсутствии членства") {
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn(null)

            shouldThrow<ForbiddenException> {
                controller.search(principal, LogSearchRequest(projectId))
            }

            verify(logSearchService, never()).search(any())
        }

        it("разрешает доступ для роли READER") {
            whenever(membershipService.getMembership(1L, projectId, "test-token")).thenReturn("READER")
            whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

            val result = controller.search(principal, LogSearchRequest(projectId))

            result.statusCode shouldBe HttpStatus.OK
        }

        it("передаёт все фильтры в сервис поиска") {
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn("ADMIN")
            whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

            controller.search(
                principal,
                LogSearchRequest(projectId, level = "ERROR", message = "timeout", from = 1000L, to = 9000L, page = 1, size = 20)
            )

            verify(logSearchService).search(argThat { request ->
                request.projectId == projectId &&
                    request.level == "ERROR" &&
                    request.message == "timeout" &&
                    request.from == 1000L &&
                    request.to == 9000L &&
                    request.page == 1 &&
                    request.size == 20
            })
        }

        it("ограничивает размер страницы до 500") {
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn("OWNER")
            whenever(logSearchService.search(any())).thenReturn(emptySearchResponse)

            controller.search(principal, LogSearchRequest(projectId, size = 9999))

            verify(logSearchService).search(argThat { request -> request.size == 500 })
        }

        it("возвращает результаты из сервиса поиска") {
            val entries = listOf(
                LogEntry(id = "id-1", ingestionId = "ing-1", level = "INFO", message = "msg", timestamp = 1000L)
            )
            val response = LogSearchResponse(items = entries, total = 1L, page = 0, size = 50)
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn("OWNER")
            whenever(logSearchService.search(any())).thenReturn(response)

            val result = controller.search(principal, LogSearchRequest(projectId))

            result.body!!.items.size shouldBe 1
            result.body!!.total shouldBe 1L
        }
    }

    describe("timeline") {

        it("возвращает 200 для члена проекта") {
            whenever(membershipService.getMembership(1L, projectId, "test-token")).thenReturn("OWNER")
            whenever(timelineService.getTimeline(any())).thenReturn(emptyTimelineResponse)

            val result = controller.timeline(principal, TimelineRequest(projectId, 0L, 1000L, 60000L))

            result.statusCode shouldBe HttpStatus.OK
        }

        it("бросает ForbiddenException при отсутствии членства") {
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn(null)

            shouldThrow<ForbiddenException> {
                controller.timeline(principal, TimelineRequest(projectId, 0L, 1000L, 60000L))
            }

            verify(timelineService, never()).getTimeline(any())
        }

        it("передаёт параметры в timeline service") {
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn("ADMIN")
            whenever(timelineService.getTimeline(any())).thenReturn(emptyTimelineResponse)

            controller.timeline(principal, TimelineRequest(projectId, from = 100L, to = 900L, bucketMs = 60000L, level = "ERROR"))

            verify(timelineService).getTimeline(argThat { req ->
                req.projectId == projectId && req.from == 100L && req.to == 900L &&
                    req.bucketMs == 60000L && req.level == "ERROR"
            })
        }

        it("возвращает бакеты из timeline service") {
            val buckets = listOf(TimelineBucket(0L, "INFO", 5L), TimelineBucket(60000L, "ERROR", 2L))
            whenever(membershipService.getMembership(any(), any(), any())).thenReturn("OWNER")
            whenever(timelineService.getTimeline(any())).thenReturn(TimelineResponse(buckets))

            val result = controller.timeline(principal, TimelineRequest(projectId, 0L, 120000L, 60000L))

            result.body!!.buckets.size shouldBe 2
        }
    }
})
