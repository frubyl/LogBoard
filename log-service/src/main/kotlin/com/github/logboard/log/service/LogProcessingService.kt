package com.github.logboard.log.service

import com.github.logboard.log.model.LogDocument
import com.github.logboard.log.model.LogDocumentEs
import com.github.logboard.log.model.RawLog
import com.github.logboard.log.repository.ClickHouseLogRepository
import com.github.logboard.log.repository.RawLogRepository
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class LogProcessingService(
    private val rawLogRepository: RawLogRepository,
    private val clickHouseLogRepository: ClickHouseLogRepository,
    private val elasticsearchOperations: ElasticsearchOperations
) {

    private val logger = LoggerFactory.getLogger(LogProcessingService::class.java)

    @Async
    @Transactional
    fun processAsync(ingestionId: UUID) {
        val rawLogs = rawLogRepository.findByIngestionId(ingestionId)
        if (rawLogs.isEmpty()) return

        try {
            clickHouseLogRepository.bulkInsert(rawLogs.map { it.toLogDocument() })
            rawLogs.forEach { elasticsearchOperations.save(it.toLogDocumentEs()) }

            val now = LocalDateTime.now()
            rawLogs.forEach { it.processedAt = now }
            rawLogRepository.saveAll(rawLogs)

            logger.debug("Processed ingestion $ingestionId: ${rawLogs.size} log(s)")
        } catch (e: Exception) {
            logger.error("Failed to process ingestion $ingestionId", e)
        }
    }

    private fun RawLog.toLogDocument() = LogDocument(
        id = id.toString(),
        projectId = projectId.toString(),
        ingestionId = ingestionId.toString(),
        level = level,
        message = message,
        timestamp = timestamp
    )

    private fun RawLog.toLogDocumentEs() = LogDocumentEs(
        id = id.toString(),
        projectId = projectId.toString(),
        ingestionId = ingestionId.toString(),
        level = level,
        message = message,
        timestamp = timestamp
    )
}
