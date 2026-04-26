package com.github.logboard.log.dto

import com.github.logboard.log.model.LogLevel
import java.time.LocalDateTime

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val timestamp: LocalDateTime
)
