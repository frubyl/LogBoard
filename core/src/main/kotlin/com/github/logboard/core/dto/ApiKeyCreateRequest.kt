package com.github.logboard.core.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.*

data class ApiKeyCreateRequest(
    @field:NotNull(message = "Project ID is required")
    val projectId: UUID?,

    @field:NotBlank(message = "Key name is required")
    @field:Size(min = 1, max = 100, message = "Key name must be between 1 and 100 characters")
    val name: String?,

    val expiresAt: LocalDateTime?
)
