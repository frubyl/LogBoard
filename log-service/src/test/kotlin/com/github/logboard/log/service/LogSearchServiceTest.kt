package com.github.logboard.log.service

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogTimelineRequest
import com.github.logboard.log.exception.common.ForbiddenException
import com.github.logboard.log.exception.common.NotFoundException
import com.github.logboard.log.model.LocalProjectMember
import com.github.logboard.log.model.LogDocument
import com.github.logboard.log.model.LogLevel
import com.github.logboard.log.repository.ElasticsearchLogRepository
import com.github.logboard.log.repository.LocalProjectMemberRepository
import com.github.logboard.log.repository.TimelineRow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class LogSearchServiceTest : DescribeSpec({

    val elasticsearchRepo = mock<ElasticsearchLogRepository>()
    val projectMemberRepo = mock<LocalProjectMemberRepository>()
    val service = LogSearchService(elasticsearchRepo, projectMemberRepo)

    val projectId = UUID.randomUUID()
    val userId = 1L
    val from = LocalDateTime.now().minusHours(1)
    val to = LocalDateTime.now()

    beforeEach {
        whenever(projectMemberRepo.existsByProjectId(projectId)).thenReturn(true)
        whenever(projectMemberRepo.findByProjectIdAndUserId(projectId, userId))
            .thenReturn(LocalProjectMember(projectId, userId, "READER"))
    }

    describe("search") {
        val sampleDoc = LogDocument(
            id = UUID.randomUUID().toString(),
            projectId = projectId.toString(),
            ingestionId = UUID.randomUUID().toString(),
            level = LogLevel.INFO.name,
            message = "test message",
            timestamp = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
        )

        it("returns logs for authorized user") {
            val request = LogSearchRequest(projectId = projectId, from = from, to = to)
            whenever(elasticsearchRepo.search(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
                .thenReturn(listOf(sampleDoc))
            whenever(elasticsearchRepo.count(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(1L)

            val response = service.search(request, userId)

            response.logs.size shouldBe 1
            response.totalCount shouldBe 1L
            response.nextCursor shouldBe null
        }

        it("returns nextCursor when there are more pages") {
            val size = 2
            val request = LogSearchRequest(projectId = projectId, from = from, to = to, size = size)
            val docs = (1..3).map { sampleDoc.copy(id = UUID.randomUUID().toString()) }
            whenever(elasticsearchRepo.search(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq(size + 1)))
                .thenReturn(docs)
            whenever(elasticsearchRepo.count(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(3L)

            val response = service.search(request, userId)

            response.logs.size shouldBe size
            response.nextCursor shouldNotBe null
        }

        it("throws ForbiddenException for non-member user") {
            val nonMemberUserId = 99L
            whenever(projectMemberRepo.findByProjectIdAndUserId(projectId, nonMemberUserId)).thenReturn(null)

            shouldThrow<ForbiddenException> {
                service.search(LogSearchRequest(projectId = projectId, from = from, to = to), nonMemberUserId)
            }
        }

        it("throws NotFoundException for non-existent project") {
            val unknownProjectId = UUID.randomUUID()
            whenever(projectMemberRepo.existsByProjectId(unknownProjectId)).thenReturn(false)

            shouldThrow<NotFoundException> {
                service.search(LogSearchRequest(projectId = unknownProjectId, from = from, to = to), userId)
            }
        }
    }

    describe("timeline") {
        it("returns timeline buckets for authorized user") {
            val request = LogTimelineRequest(projectId = projectId, from = from, to = to)
            val rows = listOf(TimelineRow(timestamp = from, totalCount = 10, errorCount = 2, warnCount = 3))
            whenever(elasticsearchRepo.timeline(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(rows)

            val response = service.timeline(request, userId)

            response.size shouldBe 1
            response[0].totalCount shouldBe 10
            response[0].errorCount shouldBe 2
        }
    }
})
