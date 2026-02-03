package com.github.logboard.core.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ProjectCreateRequest(
    @field:NotBlank(message = "Project name is required")
    @field:Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    val name: String,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null
)
