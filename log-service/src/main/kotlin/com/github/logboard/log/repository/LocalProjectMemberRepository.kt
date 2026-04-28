package com.github.logboard.log.repository

import com.github.logboard.log.model.LocalProjectMember
import com.github.logboard.log.model.LocalProjectMemberId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LocalProjectMemberRepository : JpaRepository<LocalProjectMember, LocalProjectMemberId> {
    fun findByProjectIdAndUserId(projectId: UUID, userId: Long): LocalProjectMember?
    fun existsByProjectId(projectId: UUID): Boolean
}
