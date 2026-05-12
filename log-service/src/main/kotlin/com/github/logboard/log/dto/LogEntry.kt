package com.github.logboard.log.dto

import java.time.Instant

data class LogEntry(
    val level: String,
    val message: String,
    val timestamp: Instant
)
