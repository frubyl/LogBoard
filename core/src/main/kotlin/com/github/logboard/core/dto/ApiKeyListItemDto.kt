package com.github.logboard.core.dto

import java.time.LocalDateTime
import java.util.*

data class ApiKeyListItemDto(
    val id: UUID,
    val name: String,
    val createdBy: String,
    val expiresAt: LocalDateTime?,
    val createdAt: LocalDateTime
)
