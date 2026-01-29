package com.github.logboard.core.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AuthRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9_]+$",
        message = "Username can only contain letters, numbers, and underscores"
    )
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]+$",
        message = "Password must contain at least one letter and one digit"
    )
    val password: String
)
