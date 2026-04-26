package com.github.logboard.log.service

import com.github.logboard.log.dto.LogIngestItem
import com.github.logboard.log.dto.LogIngestRequest
import com.github.logboard.log.exception.common.ForbiddenException
import com.github.logboard.log.exception.common.NotFoundException
import com.github.logboard.log.model.IngestionState
import com.github.logboard.log.model.IngestionStatus
import com.github.logboard.log.model.LogLevel
import com.github.logboard.log.repository.ElasticsearchLogRepository
import com.github.logboard.log.repository.IngestionStatusRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class LogIngestionServiceTest : DescribeSpec({

    val ingestionStatusRepository = mock<IngestionStatusRepository>()
    val elasticsearchLogRepository = mock<ElasticsearchLogRepository>()
    val service = LogIngestionService(ingestionStatusRepository, elasticsearchLogRepository)

    val projectId = UUID.randomUUID()

    beforeEach {
        whenever(ingestionStatusRepository.save(any())).thenAnswer { it.arguments[0] as IngestionStatus }
    }

    describe("ingest") {
        val items = listOf(
            LogIngestItem(level = LogLevel.INFO, message = "hello", timestamp = null),
            LogIngestItem(level = LogLevel.ERROR, message = "error!", timestamp = null)
        )

        it("creates ingestion status and returns ID") {
            val request = LogIngestRequest(projectId = projectId, logs = items)
            val response = service.ingest(request, projectId)

            response.ingestionId shouldNotBe null

            val captor = argumentCaptor<IngestionStatus>()
            verify(ingestionStatusRepository).save(captor.capture())
            captor.firstValue.accepted shouldBe 2
            captor.firstValue.projectId shouldBe projectId
        }

        it("throws ForbiddenException when projectId does not match API key") {
            val otherProjectId = UUID.randomUUID()
            val request = LogIngestRequest(projectId = projectId, logs = items)

            shouldThrow<ForbiddenException> {
                service.ingest(request, otherProjectId)
            }
        }
    }

    describe("getStatus") {
        it("returns status when ingestion exists") {
            val ingestionId = UUID.randomUUID()
            val status = IngestionStatus(
                id = ingestionId,
                projectId = projectId,
                state = IngestionState.COMPLETED,
                accepted = 5,
                processed = 5
            )
            whenever(ingestionStatusRepository.findById(ingestionId)).thenReturn(Optional.of(status))

            val response = service.getStatus(ingestionId)

            response.ingestionId shouldBe ingestionId
            response.status shouldBe "completed"
            response.accepted shouldBe 5
            response.processed shouldBe 5
        }

        it("throws NotFoundException when ingestion does not exist") {
            val ingestionId = UUID.randomUUID()
            whenever(ingestionStatusRepository.findById(ingestionId)).thenReturn(Optional.empty())

            shouldThrow<NotFoundException> {
                service.getStatus(ingestionId)
            }
        }
    }
})
