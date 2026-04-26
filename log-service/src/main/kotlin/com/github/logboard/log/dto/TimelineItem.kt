package com.github.logboard.log.dto

import java.time.LocalDateTime

data class TimelineItem(
    val timestamp: LocalDateTime,
    val totalCount: Long,
    val errorCount: Long,
    val warnCount: Long
)
