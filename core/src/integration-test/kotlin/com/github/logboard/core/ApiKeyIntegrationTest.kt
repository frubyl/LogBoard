package com.github.logboard.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.core.dto.ApiKeyCreateRequest
import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.repository.ApiKeyRepository
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import com.github.logboard.core.repository.UserRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
class ApiKeyIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    @Autowired
    private lateinit var projectMemberRepository: ProjectMemberRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private fun addReaderToProject(readerUsername: String, projectId: UUID) {
        val user = userRepository.findByUsername(readerUsername)!!
        val project = projectRepository.findById(projectId).get()
        projectMemberRepository.save(
            ProjectMember(
                id = ProjectMemberId(projectId, user.id),
                project = project,
                user = user,
                role = ProjectRole.READER
            )
        )
    }

    private fun registerAndLogin(username: String, password: String = "password123"): Array<Cookie> {
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

    private fun createProject(cookies: Array<Cookie>, name: String = "Test Project"): UUID {
        val request = ProjectCreateRequest(name, "Description")
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        return UUID.fromString(response.get("id").asText())
    }

    @Test
    fun `should create api key successfully and return key value`() {
        val cookies = registerAndLogin("apikey_user1")
        val projectId = createProject(cookies)
        val request = ApiKeyCreateRequest(projectId = projectId, name = "CI Key", expiresAt = null)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.apiKey").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        response.get("apiKey").asText() shouldStartWith "lb_"

        val keyId = UUID.fromString(response.get("id").asText())
        apiKeyRepository.findById(keyId).orElse(null).shouldNotBeNull()
    }

    @Test
    fun `should list api keys for project`() {
        val cookies = registerAndLogin("apikey_user2")
        val projectId = createProject(cookies)

        val request1 = ApiKeyCreateRequest(projectId = projectId, name = "Key One", expiresAt = null)
        val request2 = ApiKeyCreateRequest(projectId = projectId, name = "Key Two", expiresAt = null)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
                .apply { cookies.forEach { cookie(it) } }
        )
        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
                .apply { cookies.forEach { cookie(it) } }
        )

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api-keys")
                .param("projectId", projectId.toString())
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].createdBy").value("apikey_user2"))
            .andExpect(jsonPath("$[0].createdAt").exists())
    }

    @Test
    fun `should revoke api key successfully and remove from database`() {
        val cookies = registerAndLogin("apikey_user3")
        val projectId = createProject(cookies)
        val createRequest = ApiKeyCreateRequest(projectId = projectId, name = "Temp Key", expiresAt = null)

        val createResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .apply { cookies.forEach { cookie(it) } }
        ).andReturn()

        val keyId = UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())
        apiKeyRepository.findById(keyId).orElse(null).shouldNotBeNull()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api-keys/$keyId")
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())

        apiKeyRepository.findById(keyId).orElse(null) shouldBe null
    }

    @Test
    fun `should return empty list when project has no api keys`() {
        val cookies = registerAndLogin("apikey_user4")
        val projectId = createProject(cookies)

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api-keys")
                .param("projectId", projectId.toString())
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `should return 401 when creating api key without authentication`() {
        val projectId = UUID.randomUUID()
        val request = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 401 when listing api keys without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api-keys")
                .param("projectId", UUID.randomUUID().toString())
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 401 when revoking api key without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api-keys/${UUID.randomUUID()}")
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 404 when creating api key for non-existent project`() {
        val cookies = registerAndLogin("apikey_user5")
        val request = ApiKeyCreateRequest(projectId = UUID.randomUUID(), name = "Key", expiresAt = null)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 404 when listing api keys for non-existent project`() {
        val cookies = registerAndLogin("apikey_user6")

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api-keys")
                .param("projectId", UUID.randomUUID().toString())
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 404 when revoking non-existent api key`() {
        val cookies = registerAndLogin("apikey_user7")

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api-keys/${UUID.randomUUID()}")
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when user not member of project tries to create api key`() {
        val ownerCookies = registerAndLogin("apikey_owner8")
        val otherCookies = registerAndLogin("apikey_other8")
        val projectId = createProject(ownerCookies)
        val request = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { otherCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when user not member of project tries to list api keys`() {
        val ownerCookies = registerAndLogin("apikey_owner9")
        val otherCookies = registerAndLogin("apikey_other9")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api-keys")
                .param("projectId", projectId.toString())
                .apply { otherCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when READER tries to create api key`() {
        val ownerCookies = registerAndLogin("apikey_owner12")
        val readerCookies = registerAndLogin("apikey_reader12")
        val projectId = createProject(ownerCookies)
        addReaderToProject("apikey_reader12", projectId)

        val request = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { readerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when READER tries to list api keys`() {
        val ownerCookies = registerAndLogin("apikey_owner13")
        val readerCookies = registerAndLogin("apikey_reader13")
        val projectId = createProject(ownerCookies)
        addReaderToProject("apikey_reader13", projectId)

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api-keys")
                .param("projectId", projectId.toString())
                .apply { readerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when READER tries to revoke api key`() {
        val ownerCookies = registerAndLogin("apikey_owner14")
        val readerCookies = registerAndLogin("apikey_reader14")
        val projectId = createProject(ownerCookies)
        addReaderToProject("apikey_reader14", projectId)

        val createRequest = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)
        val createResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andReturn()
        val keyId = UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api-keys/$keyId")
                .apply { readerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 400 when request has missing project id`() {
        val cookies = registerAndLogin("apikey_user10")

        val invalidJson = """{"name": "My Key"}"""

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isBadRequest())
    }

    @Test
    fun `should return 400 when request has blank key name`() {
        val cookies = registerAndLogin("apikey_user11")
        val projectId = createProject(cookies)

        val invalidJson = """{"projectId": "$projectId", "name": ""}"""

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isBadRequest())
    }
}
