package com.github.logboard.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import com.github.logboard.core.repository.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.*
import java.util.*

@AutoConfigureMockMvc
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    @Autowired
    private lateinit var projectMemberRepository: ProjectMemberRepository

    private fun registerAndLogin(username: String, password: String): String {
        val authRequest = AuthRequest(username, password)

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }.andExpect {
            status { isCreated() }
        }

        val loginResult = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
        }.andReturn()

        val response = objectMapper.readTree(loginResult.response.contentAsString)
        return response.get("accessToken").asText()
    }

    @Test
    fun `should create project successfully and verify database record`() {
        // Given
        val token = registerAndLogin("projectuser1", "password123")
        val request = ProjectCreateRequest("Test Project", "Test Description")

        // When
        val result = mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
        }.andReturn()

        // Then
        val response = objectMapper.readTree(result.response.contentAsString)
        val projectId = UUID.fromString(response.get("id").asText())

        val savedProject = projectRepository.findById(projectId).orElse(null)
        savedProject.shouldNotBeNull()
        savedProject.name shouldBe "Test Project"
        savedProject.description shouldBe "Test Description"

        val projectMembers = projectMemberRepository.findByProjectId(projectId)
        projectMembers shouldHaveSize 1
        projectMembers[0].role.name shouldBe "OWNER"
    }

    @Test
    fun `should list user projects successfully`() {
        // Given
        val token = registerAndLogin("projectuser2", "password123")
        val request1 = ProjectCreateRequest("Project 1", "Description 1")
        val request2 = ProjectCreateRequest("Project 2", "Description 2")

        mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(request1)
        }

        mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(request2)
        }

        // When & Then
        mockMvc.get("/api/projects") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].name") { exists() }
            jsonPath("$[0].owner") { value("projectuser2") }
        }
    }

    @Test
    fun `should delete project successfully when user is owner`() {
        // Given
        val token = registerAndLogin("projectuser3", "password123")
        val request = ProjectCreateRequest("Project to Delete", "Will be deleted")

        val createResult = mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(request)
        }.andReturn()

        val response = objectMapper.readTree(createResult.response.contentAsString)
        val projectId = UUID.fromString(response.get("id").asText())

        // When
        mockMvc.delete("/api/projects/$projectId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
        }

        // Then
        val deletedProject = projectRepository.findById(projectId).orElse(null)
        deletedProject shouldBe null

        val projectMembers = projectMemberRepository.findByProjectId(projectId)
        projectMembers shouldHaveSize 0
    }

    @Test
    fun `should return 404 when deleting non-existent project`() {
        // Given
        val token = registerAndLogin("projectuser4", "password123")
        val nonExistentId = UUID.randomUUID()

        // When & Then
        mockMvc.delete("/api/projects/$nonExistentId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `should return 401 when creating project without authentication`() {
        // Given
        val request = ProjectCreateRequest("Unauthorized Project", "Should fail")

        // When & Then
        mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should return 401 when listing projects without authentication`() {
        // When & Then
        mockMvc.get("/api/projects").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should return 403 when non-owner tries to delete project`() {
        // Given
        val ownerToken = registerAndLogin("projectowner5", "password123")
        val nonOwnerToken = registerAndLogin("nonowner5", "password123")

        val request = ProjectCreateRequest("Owner's Project", "Only owner can delete")

        val createResult = mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $ownerToken")
            content = objectMapper.writeValueAsString(request)
        }.andReturn()

        val response = objectMapper.readTree(createResult.response.contentAsString)
        val projectId = UUID.fromString(response.get("id").asText())

        // When & Then
        mockMvc.delete("/api/projects/$projectId") {
            header("Authorization", "Bearer $nonOwnerToken")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.message") { exists() }
        }

        val project = projectRepository.findById(projectId).orElse(null)
        project.shouldNotBeNull()
    }

    @Test
    fun `should return 400 when project validation fails`() {
        // Given
        val token = registerAndLogin("projectuser6", "password123")

        val emptyNameRequest = ProjectCreateRequest("", "Valid description")

        mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(emptyNameRequest)
        }.andExpect {
            status { isBadRequest() }
        }

        val longNameRequest = ProjectCreateRequest("a".repeat(101), "Valid description")

        mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(longNameRequest)
        }.andExpect {
            status { isBadRequest() }
        }

        val longDescriptionRequest = ProjectCreateRequest("Valid Name", "a".repeat(1001))

        mockMvc.post("/api/projects") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(longDescriptionRequest)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `should return empty list when user has no projects`() {
        // Given
        val token = registerAndLogin("projectuser7", "password123")

        // When & Then
        mockMvc.get("/api/projects") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(0) }
        }
    }
}
