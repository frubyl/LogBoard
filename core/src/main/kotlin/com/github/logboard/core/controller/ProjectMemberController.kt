package com.github.logboard.core.controller

import com.github.logboard.core.dto.ProjectMemberAddRequest
import com.github.logboard.core.dto.ProjectMemberDto
import com.github.logboard.core.dto.ProjectMemberRoleUpdateRequest
import com.github.logboard.core.model.User
import com.github.logboard.core.service.ProjectMemberService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/projects/{projectId}/members")
class ProjectMemberController(
    private val projectMemberService: ProjectMemberService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectMemberController::class.java)
    }

    @GetMapping
    fun getMembers(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal actor: User
    ): ResponseEntity<List<ProjectMemberDto>> {
        logger.info("Fetching members of project $projectId by user: ${actor.username}")
        val members = projectMemberService.getMembers(projectId, actor.id!!)
        return ResponseEntity.ok(members)
    }

    @PostMapping
    fun addMember(
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: ProjectMemberAddRequest,
        @AuthenticationPrincipal actor: User
    ): ResponseEntity<ProjectMemberDto> {
        logger.info("Adding member '${request.username}' to project $projectId by user: ${actor.username}")
        val member = projectMemberService.addMember(projectId, actor.id!!, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(member)
    }

    @DeleteMapping("/{userId}")
    fun removeMember(
        @PathVariable projectId: UUID,
        @PathVariable userId: Long,
        @AuthenticationPrincipal actor: User
    ): ResponseEntity<Void> {
        logger.info("Removing member $userId from project $projectId by user: ${actor.username}")
        projectMemberService.removeMember(projectId, actor.id!!, userId)
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/{userId}")
    fun updateMemberRole(
        @PathVariable projectId: UUID,
        @PathVariable userId: Long,
        @Valid @RequestBody request: ProjectMemberRoleUpdateRequest,
        @AuthenticationPrincipal actor: User
    ): ResponseEntity<ProjectMemberDto> {
        logger.info("Updating role of member $userId in project $projectId by user: ${actor.username}")
        val member = projectMemberService.updateMemberRole(projectId, actor.id!!, userId, request)
        return ResponseEntity.ok(member)
    }
}
