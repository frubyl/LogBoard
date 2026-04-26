package com.github.logboard.log.dto

import java.time.LocalDateTime

data class LogSearchResponse(
    val logs: List<LogEntry>,
    val nextCursor: LocalDateTime?,
    val totalCount: Long
)
