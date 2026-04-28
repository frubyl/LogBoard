package com.github.logboard.core.controller

import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.dto.ProjectCreateResponse
import com.github.logboard.core.dto.ProjectResponseDto
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.model.User
import com.github.logboard.core.service.ProjectService
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProjectControllerTest {

    @Mock
    private lateinit var projectService: ProjectService

    @InjectMocks
    private lateinit var projectController: ProjectController

    @Test
    fun `createProject should return 201 with project id`() {
        // Given
        val user = User(id = 1L, username = "testuser", password = "password")
        val request = ProjectCreateRequest("Test Project", "Test Description")
        val projectId = UUID.randomUUID()
        val response = ProjectCreateResponse(projectId)

        `when`(projectService.createProject(request, user.id!!)).thenReturn(response)

        // When
        val result = projectController.createProject(request, user)

        // Then
        result.statusCode shouldBe HttpStatus.CREATED
        result.body shouldBe response
        result.body?.id shouldBe projectId

        verify(projectService).createProject(request, user.id!!)
    }

    @Test
    fun `getUserProjects should return 200 with list of projects`() {
        // Given
        val user = User(id = 1L, username = "testuser", password = "password")
        val projectId = UUID.randomUUID()
        val projects = listOf(
            ProjectResponseDto(
                id = projectId,
                name = "Test Project",
                description = "Test Description",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                owner = "testuser",
                role = ProjectRole.OWNER
            )
        )

        `when`(projectService.getUserProjects(user.id!!)).thenReturn(projects)

        // When
        val result = projectController.getUserProjects(user)

        // Then
        result.statusCode shouldBe HttpStatus.OK
        result.body shouldBe projects
        result.body?.size shouldBe 1

        verify(projectService).getUserProjects(user.id!!)
    }

    @Test
    fun `deleteProject should return 200`() {
        // Given
        val user = User(id = 1L, username = "testuser", password = "password")
        val projectId = UUID.randomUUID()

        doNothing().`when`(projectService).deleteProject(projectId, user.id!!)

        // When
        val result = projectController.deleteProject(projectId, user)

        // Then
        result.statusCode shouldBe HttpStatus.OK

        verify(projectService).deleteProject(projectId, user.id!!)
    }
}
