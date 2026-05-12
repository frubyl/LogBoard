package com.github.logboard.log.dto

import java.time.Instant

data class TimelineItem(
    val timestamp: Instant,
    val totalCount: Long,
    val errorCount: Long,
    val warnCount: Long
)
