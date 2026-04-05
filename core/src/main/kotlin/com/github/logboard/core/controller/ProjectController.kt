package com.github.logboard.core.controller

import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.dto.ProjectCreateResponse
import com.github.logboard.core.dto.ProjectResponseDto
import com.github.logboard.core.model.User
import com.github.logboard.core.service.ProjectService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/projects")
class ProjectController(
    private val projectService: ProjectService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectController::class.java)
    }

    @PostMapping
    fun createProject(
        @Valid @RequestBody request: ProjectCreateRequest,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ProjectCreateResponse> {
        logger.info("Creating project: ${request.name} for user: ${user.username}")
        val response = projectService.createProject(request, user.id!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun getUserProjects(
        @AuthenticationPrincipal user: User
    ): ResponseEntity<List<ProjectResponseDto>> {
        logger.info("Fetching projects for user: ${user.username}")
        val projects = projectService.getUserProjects(user.id!!)
        return ResponseEntity.ok(projects)
    }

    @DeleteMapping("/{projectId}")
    fun deleteProject(
        @PathVariable projectId: UUID,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<Void> {
        logger.info("Deleting project: $projectId by user: ${user.username}")
        projectService.deleteProject(projectId, user.id!!)
        return ResponseEntity.ok().build()
    }
}
