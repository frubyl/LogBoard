package com.github.logboard.log.repository

import com.github.logboard.log.model.RawLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RawLogRepository : JpaRepository<RawLog, UUID> {
    fun findByIngestionId(ingestionId: UUID): List<RawLog>
}
