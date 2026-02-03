package com.github.logboard.core.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.*

data class ProjectResponseDto(
    val id: UUID,
    val name: String,
    val description: String?,
    @JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @JsonProperty("updated_at")
    val updatedAt: LocalDateTime,
    val owner: String
)
