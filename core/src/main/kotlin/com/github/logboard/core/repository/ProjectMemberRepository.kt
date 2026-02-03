package com.github.logboard.core.repository

import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProjectMemberRepository : JpaRepository<ProjectMember, ProjectMemberId> {
    fun findByProjectIdAndUserId(projectId: UUID, userId: Long): ProjectMember?
    fun findByProjectId(projectId: UUID): List<ProjectMember>
    fun deleteAllByProjectId(projectId: UUID)
}
