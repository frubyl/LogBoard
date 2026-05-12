package com.github.logboard.log.dto

import java.time.Instant
import java.util.UUID

data class LogSearchRequest(
    val projectId: UUID,
    val from: Instant,
    val to: Instant,
    val level: List<String>? = null,
    val message: String? = null,
    val size: Int = 50,
    val cursor: Instant? = null
)
