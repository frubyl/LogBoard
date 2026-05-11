package com.github.logboard.core.controller

import com.github.logboard.core.dto.MembershipResponse
import com.github.logboard.core.model.User
import com.github.logboard.core.repository.ProjectMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal")
class InternalController(
    private val projectMemberRepository: ProjectMemberRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(InternalController::class.java)
    }

    @GetMapping("/projects/{projectId}/membership")
    fun getMembership(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<MembershipResponse> {
        logger.debug("Membership check for project $projectId by user ${user.id}")
        val member = projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)
            ?: return ResponseEntity.status(403).build()
        return ResponseEntity.ok(MembershipResponse(role = member.role.name))
    }
}
