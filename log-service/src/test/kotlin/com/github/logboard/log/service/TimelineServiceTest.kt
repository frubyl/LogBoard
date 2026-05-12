package com.github.logboard.log.service

import com.github.logboard.log.dto.TimelineBucket
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.repository.ClickHouseLogRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class TimelineServiceTest : DescribeSpec({

    val repository = mock<ClickHouseLogRepository>()
    val service = TimelineService(repository)

    val projectId = UUID.randomUUID()

    beforeEach {
        clearInvocations(repository)
        whenever(repository.timeline(any(), any(), any(), any(), anyOrNull())).thenReturn(emptyList())
    }

    describe("getTimeline") {

        it("делегирует вызов в репозиторий с правильными параметрами") {
            service.getTimeline(TimelineRequest(projectId, from = 0L, to = 1000L, bucketMs = 60000L))

            verify(repository).timeline(
                eq(projectId.toString()), eq(0L), eq(1000L), eq(60000L), eq(null)
            )
        }

        it("передаёт фильтр уровня в репозиторий") {
            service.getTimeline(TimelineRequest(projectId, 0L, 1000L, 60000L, level = "ERROR"))

            verify(repository).timeline(any(), any(), any(), any(), eq("ERROR"))
        }

        it("возвращает пустой список бакетов если нет данных") {
            val result = service.getTimeline(TimelineRequest(projectId, 0L, 1000L, 60000L))

            result.buckets shouldBe emptyList()
        }

        it("маппит бакеты из репозитория в ответ") {
            val buckets = listOf(
                TimelineBucket(0L, "INFO", 5L),
                TimelineBucket(60000L, "ERROR", 2L)
            )
            whenever(repository.timeline(any(), any(), any(), any(), anyOrNull())).thenReturn(buckets)

            val result = service.getTimeline(TimelineRequest(projectId, 0L, 120000L, 60000L))

            result.buckets shouldBe buckets
        }

        it("передаёт projectId как строку в репозиторий") {
            service.getTimeline(TimelineRequest(projectId, 0L, 1000L, 60000L))

            verify(repository).timeline(eq(projectId.toString()), any(), any(), any(), anyOrNull())
        }
    }
})
