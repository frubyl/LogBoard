package com.github.logboard.log.dto

import java.util.UUID

data class LogSearchRequest(
    val projectId: UUID,
    val level: String? = null,
    val message: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val page: Int = 0,
    val size: Int = 50
)
