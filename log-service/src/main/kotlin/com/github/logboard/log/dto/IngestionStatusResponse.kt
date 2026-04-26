package com.github.logboard.log.dto

import java.time.LocalDateTime
import java.util.UUID

data class IngestionStatusResponse(
    val ingestionId: UUID,
    val status: String,
    val accepted: Int,
    val processed: Int,
    val failed: Int,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val error: String?
)
