package com.github.logboard.core.dto

import com.github.logboard.core.model.ProjectRole

data class ProjectMemberDto(
    val userId: Long,
    val username: String,
    val role: ProjectRole
)
