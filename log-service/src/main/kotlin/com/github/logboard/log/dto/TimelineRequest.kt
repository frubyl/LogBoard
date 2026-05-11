package com.github.logboard.log.dto

import java.util.UUID

data class TimelineRequest(
    val projectId: UUID,
    val from: Long,
    val to: Long,
    val bucketMs: Long,
    val level: String? = null
)
