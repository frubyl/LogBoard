package com.github.logboard.core.service

import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.dto.ProjectCreateResponse
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.Project
import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.model.User
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProjectServiceTest {

    @Mock
    private lateinit var projectRepository: ProjectRepository

    @Mock
    private lateinit var projectMemberRepository: ProjectMemberRepository

    @Mock
    private lateinit var userService: UserService

    @InjectMocks
    private lateinit var projectService: ProjectService

    private lateinit var user: User
    private lateinit var project: Project
    private lateinit var projectMember: ProjectMember

    @BeforeEach
    fun setUp() {
        user = User(
            id = 1L,
            username = "testuser",
            password = "encodedPassword",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        project = Project(
            id = UUID.randomUUID(),
            name = "Test Project",
            description = "Test Description",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        projectMember = ProjectMember(
            id = ProjectMemberId(project.id, user.id),
            project = project,
            user = user,
            role = ProjectRole.OWNER,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    @Test
    fun `createProject should create project and assign user as owner`() {
        // Given
        val request = ProjectCreateRequest(
            name = "Test Project",
            description = "Test Description"
        )

        `when`(userService.loadUserById(user.id!!)).thenReturn(user)
        `when`(projectRepository.save(any(Project::class.java))).thenReturn(project)
        `when`(projectMemberRepository.save(any(ProjectMember::class.java))).thenReturn(projectMember)

        // When
        val result = projectService.createProject(request, user.id!!)

        // Then
        result.shouldNotBeNull()
        result.id shouldBe project.id
        verify(userService).loadUserById(user.id!!)
        verify(projectRepository).save(any(Project::class.java))
        verify(projectMemberRepository).save(any(ProjectMember::class.java))
    }

    @Test
    fun `getUserProjects should return list of projects for user`() {
        // Given
        project.members.add(projectMember)
        val projects = listOf(project)

        `when`(projectRepository.findAllByUserId(user.id!!)).thenReturn(projects)

        // When
        val result = projectService.getUserProjects(user.id!!)

        // Then
        result.shouldNotBeNull()
        result.size shouldBe 1
        result[0].id shouldBe project.id
        result[0].name shouldBe project.name
        result[0].owner shouldBe user.username
        verify(projectRepository).findAllByUserId(user.id!!)
    }

    @Test
    fun `deleteProject should delete project when user is owner`() {
        // Given
        val projectId = project.id!!

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(projectMember)
        doNothing().`when`(projectMemberRepository).deleteAllByProjectId(projectId)
        doNothing().`when`(projectRepository).delete(project)

        // When
        projectService.deleteProject(projectId, user.id!!)

        // Then
        verify(projectRepository).findById(projectId)
        verify(projectMemberRepository).findByProjectIdAndUserId(projectId, user.id!!)
        verify(projectMemberRepository).deleteAllByProjectId(projectId)
        verify(projectRepository).delete(project)
    }

    @Test
    fun `deleteProject should throw ForbiddenException when user is not owner`() {
        // Given
        val projectId = project.id!!
        val adminMember = ProjectMember(
            id = ProjectMemberId(project.id, user.id),
            project = project,
            user = user,
            role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(adminMember)

        // When & Then
        val exception = shouldThrow<ForbiddenException> {
            projectService.deleteProject(projectId, user.id!!)
        }

        exception.message shouldBe "Only project owner can delete the project"
        verify(projectRepository).findById(projectId)
        verify(projectMemberRepository).findByProjectIdAndUserId(projectId, user.id!!)
        verify(projectRepository, never()).delete(any(Project::class.java))
    }

    @Test
    fun `deleteProject should throw NotFoundException when project does not exist`() {
        // Given
        val projectId = UUID.randomUUID()

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.empty())

        // When & Then
        val exception = shouldThrow<NotFoundException> {
            projectService.deleteProject(projectId, user.id!!)
        }

        exception.message shouldBe "Project not found with id: $projectId"
        verify(projectRepository).findById(projectId)
        verify(projectRepository, never()).delete(any(Project::class.java))
    }

    @Test
    fun `deleteProject should throw ForbiddenException when user is not a member`() {
        // Given
        val projectId = project.id!!

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(null)

        // When & Then
        val exception = shouldThrow<ForbiddenException> {
            projectService.deleteProject(projectId, user.id!!)
        }

        exception.message shouldBe "User is not a member of this project"
        verify(projectRepository).findById(projectId)
        verify(projectMemberRepository).findByProjectIdAndUserId(projectId, user.id!!)
        verify(projectRepository, never()).delete(any(Project::class.java))
    }
}
