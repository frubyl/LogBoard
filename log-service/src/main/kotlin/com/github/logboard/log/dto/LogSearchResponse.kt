package com.github.logboard.log.dto

import java.time.Instant

data class LogSearchResponse(
    val logs: List<LogEntry>,
    val totalCount: Long,
    val nextCursor: Instant? = null
)
