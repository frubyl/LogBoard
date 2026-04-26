package com.github.logboard.core.dto

import com.github.logboard.core.model.ProjectRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ProjectMemberAddRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotNull(message = "Role is required")
    val role: ProjectRole
)
