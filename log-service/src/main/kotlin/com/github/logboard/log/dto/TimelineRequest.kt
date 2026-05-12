package com.github.logboard.log.dto

import java.time.Instant
import java.util.UUID

data class TimelineRequest(
    val projectId: UUID,
    val from: Instant,
    val to: Instant,
    val level: List<String>? = null,
    val message: String? = null
)
