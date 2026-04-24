package com.github.logboard.core.dto

import com.github.logboard.core.model.ProjectRole
import jakarta.validation.constraints.NotNull

data class ProjectMemberRoleUpdateRequest(
    @field:NotNull(message = "Role is required")
    val role: ProjectRole
)
