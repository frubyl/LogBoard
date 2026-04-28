package com.github.logboard.core.service

import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.dto.ProjectCreateResponse
import com.github.logboard.core.dto.ProjectResponseDto
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.Project
import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userService: UserService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectService::class.java)
    }

    @Transactional
    fun createProject(request: ProjectCreateRequest, userId: Long): ProjectCreateResponse {
        logger.info("Creating project '${request.name}' for user id: $userId")

        val user = userService.loadUserById(userId)

        val project = Project(
            name = request.name,
            description = request.description
        )

        val savedProject = projectRepository.save(project)

        val projectMember = ProjectMember(
            id = ProjectMemberId(savedProject.id, user.id),
            project = savedProject,
            user = user,
            role = ProjectRole.OWNER
        )

        projectMemberRepository.save(projectMember)

        logger.info("Project created successfully with id: ${savedProject.id}")
        return ProjectCreateResponse(savedProject.id!!)
    }

    fun getUserProjects(userId: Long): List<ProjectResponseDto> {
        logger.info("Fetching projects for user id: $userId")

        val projects = projectRepository.findAllByUserId(userId)

        return projects.map { project ->
            val owner = project.members
                .firstOrNull { it.role == ProjectRole.OWNER }
                ?.user
                ?.username
                ?: "Unknown"

            val role = project.members
                .firstOrNull { it.user?.id == userId }
                ?.role
                ?: ProjectRole.READER

            ProjectResponseDto(
                id = project.id!!,
                name = project.name,
                description = project.description,
                createdAt = project.createdAt!!,
                updatedAt = project.updatedAt!!,
                owner = owner,
                role = role
            )
        }
    }

    @Transactional
    fun deleteProject(projectId: UUID, userId: Long) {
        logger.info("Attempting to delete project $projectId by user id: $userId")

        val project = projectRepository.findById(projectId)
            .orElseThrow { NotFoundException("Project not found with id: $projectId") }

        val projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (projectMember.role != ProjectRole.OWNER) {
            throw ForbiddenException("Only project owner can delete the project")
        }

        projectMemberRepository.deleteAllByProjectId(projectId)
        projectRepository.delete(project)

        logger.info("Project $projectId deleted successfully")
    }
}
