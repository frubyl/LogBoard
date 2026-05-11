package com.github.logboard.log.dto

data class LogSearchResponse(
    val items: List<LogEntry>,
    val total: Long,
    val page: Int,
    val size: Int
)
