package com.github.logboard.core.service

import com.github.logboard.core.dto.ProjectMemberAddRequest
import com.github.logboard.core.dto.ProjectMemberDto
import com.github.logboard.core.dto.ProjectMemberRoleUpdateRequest
import com.github.logboard.core.event.KafkaTopics
import com.github.logboard.core.event.ProjectMemberEvent
import com.github.logboard.core.exception.common.AlreadyExistsException
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ProjectMemberService(
    private val projectRepository: ProjectRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userService: UserService,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectMemberService::class.java)
    }

    @Transactional(readOnly = true)
    fun getMembers(projectId: UUID, actorId: Long): List<ProjectMemberDto> {
        logger.info("Fetching members of project $projectId by actor id: $actorId")

        if (!projectRepository.existsById(projectId)) {
            throw NotFoundException("Project not found with id: $projectId")
        }

        val actor = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (actor.role == ProjectRole.READER) {
            throw ForbiddenException("Only OWNER or ADMIN can view members")
        }

        return projectMemberRepository.findByProjectId(projectId).map { member ->
            ProjectMemberDto(
                userId = member.user!!.id!!,
                username = member.user!!.username,
                role = member.role
            )
        }
    }

    @Transactional
    fun addMember(projectId: UUID, actorId: Long, request: ProjectMemberAddRequest): ProjectMemberDto {
        logger.info("Adding member '${request.username}' with role ${request.role} to project $projectId by actor id: $actorId")

        if (request.role == ProjectRole.OWNER) {
            throw ForbiddenException("Cannot assign OWNER role when adding a member")
        }

        val project = projectRepository.findById(projectId)
            .orElseThrow { NotFoundException("Project not found with id: $projectId") }

        val actor = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (actor.role == ProjectRole.READER) {
            throw ForbiddenException("Only OWNER or ADMIN can add members")
        }

        if (actor.role == ProjectRole.ADMIN && request.role != ProjectRole.READER) {
            throw ForbiddenException("ADMIN can only add members with READER role")
        }

        val newUser = userService.loadUserByUsername(request.username)

        if (projectMemberRepository.findByProjectIdAndUserId(projectId, newUser.id!!) != null) {
            throw AlreadyExistsException("User '${request.username}' is already a member of this project")
        }

        val member = ProjectMember(
            id = ProjectMemberId(project.id, newUser.id),
            project = project,
            user = newUser,
            role = request.role
        )

        val saved = projectMemberRepository.save(member)
        logger.info("Member '${request.username}' added to project $projectId with role ${request.role}")

        kafkaTemplate.send(
            KafkaTopics.PROJECT_MEMBERS,
            "$projectId:${newUser.id}",
            ProjectMemberEvent(
                eventType = ProjectMemberEvent.EventType.ADDED,
                projectId = projectId,
                userId = newUser.id!!,
                role = saved.role.name
            )
        )

        return ProjectMemberDto(
            userId = newUser.id!!,
            username = newUser.username,
            role = saved.role
        )
    }

    @Transactional
    fun removeMember(projectId: UUID, actorId: Long, targetUserId: Long) {
        logger.info("Removing member $targetUserId from project $projectId by actor id: $actorId")

        if (!projectRepository.existsById(projectId)) {
            throw NotFoundException("Project not found with id: $projectId")
        }

        val actor = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (actor.role == ProjectRole.READER) {
            throw ForbiddenException("Only OWNER or ADMIN can remove members")
        }

        val target = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
            ?: throw NotFoundException("User $targetUserId is not a member of project $projectId")

        if (target.role == ProjectRole.OWNER) {
            throw ForbiddenException("Cannot remove the project OWNER")
        }

        if (actor.role == ProjectRole.ADMIN && target.role != ProjectRole.READER) {
            throw ForbiddenException("ADMIN can only remove members with READER role")
        }

        projectMemberRepository.delete(target)
        logger.info("Member $targetUserId removed from project $projectId")

        kafkaTemplate.send(
            KafkaTopics.PROJECT_MEMBERS,
            "$projectId:$targetUserId",
            ProjectMemberEvent(
                eventType = ProjectMemberEvent.EventType.REMOVED,
                projectId = projectId,
                userId = targetUserId
            )
        )
    }

    @Transactional
    fun updateMemberRole(projectId: UUID, actorId: Long, targetUserId: Long, request: ProjectMemberRoleUpdateRequest): ProjectMemberDto {
        logger.info("Updating role of member $targetUserId in project $projectId to ${request.role} by actor id: $actorId")

        if (request.role == ProjectRole.OWNER) {
            throw ForbiddenException("Cannot assign OWNER role")
        }

        if (!projectRepository.existsById(projectId)) {
            throw NotFoundException("Project not found with id: $projectId")
        }

        val actor = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (actor.role != ProjectRole.OWNER) {
            throw ForbiddenException("Only OWNER can change member roles")
        }

        if (actorId == targetUserId) {
            throw ForbiddenException("Cannot change your own role")
        }

        val target = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
            ?: throw NotFoundException("User $targetUserId is not a member of project $projectId")

        if (target.role == ProjectRole.OWNER) {
            throw ForbiddenException("Cannot change the role of the project OWNER")
        }

        target.role = request.role
        val saved = projectMemberRepository.save(target)
        logger.info("Role of member $targetUserId in project $projectId updated to ${request.role}")

        kafkaTemplate.send(
            KafkaTopics.PROJECT_MEMBERS,
            "$projectId:$targetUserId",
            ProjectMemberEvent(
                eventType = ProjectMemberEvent.EventType.ROLE_CHANGED,
                projectId = projectId,
                userId = targetUserId,
                role = saved.role.name
            )
        )

        return ProjectMemberDto(
            userId = targetUserId,
            username = target.user?.username ?: "Unknown",
            role = saved.role
        )
    }
}
