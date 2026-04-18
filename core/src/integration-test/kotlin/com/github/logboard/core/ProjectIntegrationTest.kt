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
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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

    private fun registerAndLogin(username: String, password: String): Array<Cookie> {
        val authRequest = AuthRequest(username, password)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
        ).andExpect(status().isCreated())

        val loginResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
        ).andExpect(status().isOk()).andReturn()

        return loginResult.response.cookies
    }

    @Test
    fun `should create project successfully and verify database record`() {
        val cookies = registerAndLogin("projectuser1", "password123")
        val request = ProjectCreateRequest("Test Project", "Test Description")

        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect {
            status().isCreated()
            jsonPath("$.id").exists()
        }.andReturn()

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
        val cookies = registerAndLogin("projectuser2", "password123")
        val request1 = ProjectCreateRequest("Project 1", "Description 1")
        val request2 = ProjectCreateRequest("Project 2", "Description 2")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
                .apply { cookies.forEach { cookie(it) } }
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
                .apply { cookies.forEach { cookie(it) } }
        )

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects")
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect {
            status().isOk()
            jsonPath("$").isArray
            jsonPath("$.length()").value(2)
            jsonPath("$[0].name").exists()
            jsonPath("$[0].owner").value("projectuser2")
        }
    }

    @Test
    fun `should delete project successfully when user is owner`() {
        val cookies = registerAndLogin("projectuser3", "password123")
        val request = ProjectCreateRequest("Project to Delete", "Will be deleted")

        val createResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { cookies.forEach { cookie(it) } }
        ).andReturn()

        val response = objectMapper.readTree(createResult.response.contentAsString)
        val projectId = UUID.fromString(response.get("id").asText())

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId")
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect { status().isOk() }

        val deletedProject = projectRepository.findById(projectId).orElse(null)
        deletedProject shouldBe null

        val projectMembers = projectMemberRepository.findByProjectId(projectId)
        projectMembers shouldHaveSize 0
    }

    @Test
    fun `should return 404 when deleting non-existent project`() {
        val cookies = registerAndLogin("projectuser4", "password123")
        val nonExistentId = UUID.randomUUID()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$nonExistentId")
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect {
            status().isNotFound()
            jsonPath("$.message").exists()
        }
    }

    @Test
    fun `should return 401 when creating project without authentication`() {
        val request = ProjectCreateRequest("Unauthorized Project", "Should fail")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect { status().isUnauthorized() }
    }

    @Test
    fun `should return 401 when listing projects without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects")
        ).andExpect { status().isUnauthorized() }
    }

    @Test
    fun `should return 403 when non-owner tries to delete project`() {
        val ownerCookies = registerAndLogin("projectowner5", "password123")
        val nonOwnerCookies = registerAndLogin("nonowner5", "password123")

        val request = ProjectCreateRequest("Owner's Project", "Only owner can delete")

        val createResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andReturn()

        val response = objectMapper.readTree(createResult.response.contentAsString)
        val projectId = UUID.fromString(response.get("id").asText())

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId")
                .apply { nonOwnerCookies.forEach { cookie(it) } }
        ).andExpect {
            status().isForbidden()
            jsonPath("$.message").exists()
        }

        val project = projectRepository.findById(projectId).orElse(null)
        project.shouldNotBeNull()
    }

    @Test
    fun `should return 400 when project validation fails`() {
        val cookies = registerAndLogin("projectuser6", "password123")

        val emptyNameRequest = ProjectCreateRequest("", "Valid description")
        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyNameRequest))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect { status().isBadRequest() }

        val longNameRequest = ProjectCreateRequest("a".repeat(101), "Valid description")
        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longNameRequest))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect { status().isBadRequest() }

        val longDescriptionRequest = ProjectCreateRequest("Valid Name", "a".repeat(1001))
        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longDescriptionRequest))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect { status().isBadRequest() }
    }

    @Test
    fun `should return empty list when user has no projects`() {
        val cookies = registerAndLogin("projectuser7", "password123")

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects")
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect {
            status().isOk()
            jsonPath("$").isArray
            jsonPath("$.length()").value(0)
        }
    }
}
