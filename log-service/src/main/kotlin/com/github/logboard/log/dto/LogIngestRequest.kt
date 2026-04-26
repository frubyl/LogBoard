package com.github.logboard.log.dto

import com.github.logboard.log.model.LogLevel
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

data class LogIngestRequest(
    @field:NotNull val projectId: UUID?,
    @field:NotNull @field:Size(min = 1, max = 1000) @field:Valid val logs: List<LogIngestItem>?
)

data class LogIngestItem(
    @field:NotNull val level: LogLevel?,
    @field:NotNull @field:Size(min = 1, max = 10000) val message: String?,
    val timestamp: LocalDateTime?
)
