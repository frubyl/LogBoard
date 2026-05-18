package com.github.logboard.log.service

import com.github.logboard.log.dto.IngestEntry
import com.github.logboard.log.model.RawLog
import com.github.logboard.log.repository.RawLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class IngestionService(
    private val rawLogRepository: RawLogRepository,
    private val logProcessingService: LogProcessingService
) {

    @Transactional
    fun ingest(projectId: UUID, entries: List<IngestEntry>): UUID {
        val ingestionId = UUID.randomUUID()
        val rawLogs = entries.map { entry ->
            RawLog(
                projectId = projectId,
                ingestionId = ingestionId,
                level = entry.level,
                message = entry.message,
                timestamp = entry.timestamp.toEpochMilli()
            )
        }
        rawLogRepository.saveAll(rawLogs)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                logProcessingService.processAsync(ingestionId)
            }
        })
        return ingestionId
    }
}
