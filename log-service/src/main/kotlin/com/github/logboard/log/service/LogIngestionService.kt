package com.github.logboard.log.service

import com.github.logboard.log.dto.LogIngestItem
import com.github.logboard.log.dto.LogIngestRequest
import com.github.logboard.log.dto.LogIngestResponse
import com.github.logboard.log.dto.IngestionStatusResponse
import com.github.logboard.log.exception.common.ForbiddenException
import com.github.logboard.log.exception.common.NotFoundException
import com.github.logboard.log.model.IngestionState
import com.github.logboard.log.model.IngestionStatus
import com.github.logboard.log.model.LogDocument
import com.github.logboard.log.repository.ElasticsearchLogRepository
import com.github.logboard.log.repository.IngestionStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class LogIngestionService(
    private val ingestionStatusRepository: IngestionStatusRepository,
    private val elasticsearchLogRepository: ElasticsearchLogRepository
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(LogIngestionService::class.java)
    }

    @Transactional
    fun ingest(request: LogIngestRequest, apiKeyProjectId: UUID): LogIngestResponse {
        val projectId = request.projectId!!
        val logs = request.logs!!

        if (projectId != apiKeyProjectId) {
            throw ForbiddenException("API key does not belong to project $projectId")
        }

        val status = IngestionStatus(
            projectId = projectId,
            state = IngestionState.PENDING,
            accepted = logs.size
        )
        ingestionStatusRepository.save(status)

        processLogsAsync(status.id, projectId, logs)

        logger.info("Ingestion ${status.id} accepted: ${logs.size} logs for project $projectId")
        return LogIngestResponse(ingestionId = status.id)
    }

    fun getStatus(ingestionId: UUID): IngestionStatusResponse {
        val status = ingestionStatusRepository.findById(ingestionId)
            .orElseThrow { NotFoundException("Ingestion not found: $ingestionId") }
        return status.toResponse()
    }

    @Async("logIngestionExecutor")
    fun processLogsAsync(ingestionId: UUID, projectId: UUID, items: List<LogIngestItem>) {
        val status = ingestionStatusRepository.findById(ingestionId).orElse(null) ?: return

        try {
            status.state = IngestionState.PROCESSING
            ingestionStatusRepository.save(status)

            val docs = items.map { item ->
                val ts = (item.timestamp ?: LocalDateTime.now()).toInstant(ZoneOffset.UTC).toEpochMilli()
                LogDocument(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId.toString(),
                    ingestionId = ingestionId.toString(),
                    level = item.level!!.name,
                    message = item.message!!,
                    timestamp = ts
                )
            }

            elasticsearchLogRepository.bulkIndex(docs)

            status.state = IngestionState.COMPLETED
            status.processed = docs.size
            status.completedAt = LocalDateTime.now()
            ingestionStatusRepository.save(status)

            logger.info("Ingestion $ingestionId completed: ${docs.size} logs indexed")
        } catch (e: Exception) {
            logger.error("Ingestion $ingestionId failed: ${e.message}", e)
            status.state = IngestionState.FAILED
            status.failed = items.size
            status.error = e.message
            status.completedAt = LocalDateTime.now()
            ingestionStatusRepository.save(status)
        }
    }

    private fun IngestionStatus.toResponse() = IngestionStatusResponse(
        ingestionId = id,
        status = state.name.lowercase(),
        accepted = accepted,
        processed = processed,
        failed = failed,
        startedAt = startedAt,
        completedAt = completedAt,
        error = error
    )
}
