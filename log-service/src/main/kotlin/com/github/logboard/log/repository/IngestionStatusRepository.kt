package com.github.logboard.log.repository

import com.github.logboard.log.model.IngestionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface IngestionStatusRepository : JpaRepository<IngestionStatus, UUID>
