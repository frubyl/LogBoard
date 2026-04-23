package com.github.logboard.core.dto

import java.time.LocalDateTime
import java.util.*

data class ApiKeyCreateResponse(
    val id: UUID,
    val apiKey: String,
    val createdAt: LocalDateTime
)
