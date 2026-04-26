package com.github.logboard.log.dto

import com.github.logboard.log.model.LogLevel
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime
import java.util.UUID

data class LogSearchRequest(
    @field:NotNull val projectId: UUID?,
    @field:NotNull val from: LocalDateTime?,
    @field:NotNull val to: LocalDateTime?,
    val level: List<LogLevel>? = null,
    val message: String? = null,
    @field:Positive val size: Int = 50,
    val cursor: LocalDateTime? = null
)
