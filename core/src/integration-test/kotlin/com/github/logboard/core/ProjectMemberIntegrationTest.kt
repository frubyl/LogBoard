package com.github.logboard.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.ProjectCreateRequest
import com.github.logboard.core.dto.ProjectMemberAddRequest
import com.github.logboard.core.dto.ProjectMemberRoleUpdateRequest
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.repository.ProjectMemberRepository
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
class ProjectMemberIntegrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var projectMemberRepository: ProjectMemberRepository

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

    private fun register(username: String, password: String = "password123") {
        val authRequest = AuthRequest(username, password)
        mockMvc.perform(
            MockMvcRequestBuilders.post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
        ).andExpect(status().isCreated())
    }

    private fun createProject(cookies: Array<Cookie>, name: String = "Test Project"): UUID {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectCreateRequest(name, "Description")))
                .apply { cookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        return UUID.fromString(objectMapper.readTree(result.response.contentAsString).get("id").asText())
    }

    // --- getMembers ---

    @Test
    fun `should return members list when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_gm_owner1")
        register("pm_gm_member1")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_gm_member1", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects/$projectId/members")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].userId").exists())
            .andExpect(jsonPath("$[*].username").exists())
            .andExpect(jsonPath("$[*].role").exists())
    }

    @Test
    fun `should return members list when actor is ADMIN`() {
        val ownerCookies = registerAndLogin("pm_gm_owner2")
        val adminCookies = registerAndLogin("pm_gm_admin2")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_gm_admin2", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects/$projectId/members")
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `should return 403 when READER tries to get members`() {
        val ownerCookies = registerAndLogin("pm_gm_owner3")
        val readerCookies = registerAndLogin("pm_gm_reader3")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_gm_reader3", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects/$projectId/members")
                .apply { readerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when non-member tries to get members`() {
        val ownerCookies = registerAndLogin("pm_gm_owner4")
        val outsiderCookies = registerAndLogin("pm_gm_outsider4")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects/$projectId/members")
                .apply { outsiderCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 401 when getting members without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects/${UUID.randomUUID()}/members")
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 404 when getting members of non-existent project`() {
        val ownerCookies = registerAndLogin("pm_gm_owner5")

        mockMvc.perform(
            MockMvcRequestBuilders.get("/projects/${UUID.randomUUID()}/members")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
    }

    // --- addMember ---

    @Test
    fun `should add ADMIN member successfully when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner1")
        register("pm_target1")
        val projectId = createProject(ownerCookies)

        val request = ProjectMemberAddRequest(username = "pm_target1", role = ProjectRole.ADMIN)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.username").value("pm_target1"))
            .andExpect(jsonPath("$.role").value("ADMIN"))

        val members = projectMemberRepository.findByProjectId(projectId)
        members.size shouldBe 2
    }

    @Test
    fun `should add READER member successfully when actor is ADMIN`() {
        val ownerCookies = registerAndLogin("pm_owner2")
        val adminCookies = registerAndLogin("pm_admin2")
        register("pm_target2")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin2", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target2", role = ProjectRole.READER)))
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("READER"))
    }

    @Test
    fun `should add READER member successfully when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner1b")
        register("pm_target1b")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target1b", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.username").value("pm_target1b"))
            .andExpect(jsonPath("$.role").value("READER"))

        val members = projectMemberRepository.findByProjectId(projectId)
        members.size shouldBe 2
    }

    @Test
    fun `should return 403 when READER tries to add member`() {
        val ownerCookies = registerAndLogin("pm_owner3")
        val readerCookies = registerAndLogin("pm_reader3")
        register("pm_target3")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_reader3", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target3", role = ProjectRole.READER)))
                .apply { readerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when ADMIN tries to add ADMIN`() {
        val ownerCookies = registerAndLogin("pm_owner4")
        val adminCookies = registerAndLogin("pm_admin4")
        register("pm_target4")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin4", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target4", role = ProjectRole.ADMIN)))
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 403 when non-member tries to add member`() {
        val ownerCookies = registerAndLogin("pm_owner5")
        val outsiderCookies = registerAndLogin("pm_outsider5")
        register("pm_target5")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target5", role = ProjectRole.READER)))
                .apply { outsiderCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 403 when trying to add member with OWNER role`() {
        val ownerCookies = registerAndLogin("pm_owner6")
        register("pm_target6")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target6", role = ProjectRole.OWNER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 409 when user is already a member`() {
        val ownerCookies = registerAndLogin("pm_owner7")
        register("pm_target7")
        val projectId = createProject(ownerCookies)

        val request = ProjectMemberAddRequest(username = "pm_target7", role = ProjectRole.READER)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isConflict())
    }

    @Test
    fun `should return 404 when adding member to non-existent project`() {
        val ownerCookies = registerAndLogin("pm_owner8")
        register("pm_target8")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/${UUID.randomUUID()}/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target8", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
    }

    @Test
    fun `should return 401 when adding member without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/${UUID.randomUUID()}/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "someone", role = ProjectRole.READER)))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 400 when addMember request has blank username`() {
        val ownerCookies = registerAndLogin("pm_owner9")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username": "", "role": "READER"}""")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isBadRequest())
    }

    // --- removeMember ---

    @Test
    fun `should remove ADMIN member successfully when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner10")
        register("pm_target10")
        val projectId = createProject(ownerCookies)

        val addResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target10", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/$targetUserId")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())

        val members = projectMemberRepository.findByProjectId(projectId)
        members.size shouldBe 1
    }

    @Test
    fun `should remove READER member successfully when actor is ADMIN`() {
        val ownerCookies = registerAndLogin("pm_owner11")
        val adminCookies = registerAndLogin("pm_admin11")
        register("pm_reader11")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin11", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        val addReaderResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_reader11", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val readerUserId = objectMapper.readTree(addReaderResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/$readerUserId")
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
    }

    @Test
    fun `should remove READER member successfully when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner10b")
        register("pm_target10b")
        val projectId = createProject(ownerCookies)

        val addResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target10b", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/$targetUserId")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())

        val members = projectMemberRepository.findByProjectId(projectId)
        members.size shouldBe 1
    }

    @Test
    fun `should return 403 when READER tries to remove member`() {
        val ownerCookies = registerAndLogin("pm_owner12")
        val readerCookies = registerAndLogin("pm_reader12")
        register("pm_target12")
        val projectId = createProject(ownerCookies)

        val addReaderResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_reader12", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val addTargetResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target12", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addTargetResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/$targetUserId")
                .apply { readerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 403 when ADMIN tries to remove ADMIN`() {
        val ownerCookies = registerAndLogin("pm_owner13")
        val adminCookies = registerAndLogin("pm_admin13")
        register("pm_admin13b")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin13", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        val addAdmin2Result = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin13b", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val admin2UserId = objectMapper.readTree(addAdmin2Result.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/$admin2UserId")
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 403 when trying to remove OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner14")
        val adminCookies = registerAndLogin("pm_admin14")
        val projectId = createProject(ownerCookies)

        val addAdminResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin14", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val ownerUserId = projectMemberRepository.findByProjectId(projectId)
            .first { it.role == ProjectRole.OWNER }.user!!.id!!

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/$ownerUserId")
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 404 when removing non-existent member`() {
        val ownerCookies = registerAndLogin("pm_owner15")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/$projectId/members/99999")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
    }

    @Test
    fun `should return 401 when removing member without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.delete("/projects/${UUID.randomUUID()}/members/1")
        ).andExpect(status().isUnauthorized())
    }

    // --- updateMemberRole ---

    @Test
    fun `should update member role successfully when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner16")
        register("pm_target16")
        val projectId = createProject(ownerCookies)

        val addResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target16", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/$targetUserId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(targetUserId))
            .andExpect(jsonPath("$.role").value("ADMIN"))

        val member = projectMemberRepository.findByProjectId(projectId).first { it.user!!.id == targetUserId }
        member.role shouldBe ProjectRole.ADMIN
    }

    @Test
    fun `should downgrade ADMIN to READER successfully when actor is OWNER`() {
        val ownerCookies = registerAndLogin("pm_owner16b")
        register("pm_target16b")
        val projectId = createProject(ownerCookies)

        val addResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target16b", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/$targetUserId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(targetUserId))
            .andExpect(jsonPath("$.role").value("READER"))

        val member = projectMemberRepository.findByProjectId(projectId).first { it.user!!.id == targetUserId }
        member.role shouldBe ProjectRole.READER
    }

    @Test
    fun `should return 403 when ADMIN tries to update member role`() {
        val ownerCookies = registerAndLogin("pm_owner17")
        val adminCookies = registerAndLogin("pm_admin17")
        register("pm_target17")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_admin17", role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated())

        val addTargetResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target17", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addTargetResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/$targetUserId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.ADMIN)))
                .apply { adminCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 403 when OWNER tries to assign OWNER role`() {
        val ownerCookies = registerAndLogin("pm_owner18")
        register("pm_target18")
        val projectId = createProject(ownerCookies)

        val addResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/projects/$projectId/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberAddRequest(username = "pm_target18", role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isCreated()).andReturn()

        val targetUserId = objectMapper.readTree(addResult.response.contentAsString).get("userId").asLong()

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/$targetUserId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.OWNER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 403 when OWNER tries to change own role`() {
        val ownerCookies = registerAndLogin("pm_owner19")
        val projectId = createProject(ownerCookies)

        val ownerUserId = projectMemberRepository.findByProjectId(projectId)
            .first { it.role == ProjectRole.OWNER }.user!!.id!!

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/$ownerUserId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.ADMIN)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isForbidden())
    }

    @Test
    fun `should return 404 when updating role of non-existent member`() {
        val ownerCookies = registerAndLogin("pm_owner20")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)))
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isNotFound())
    }

    @Test
    fun `should return 401 when updating role without authentication`() {
        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/${UUID.randomUUID()}/members/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 400 when updateMemberRole request has null role`() {
        val ownerCookies = registerAndLogin("pm_owner21")
        val projectId = createProject(ownerCookies)

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/projects/$projectId/members/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role": null}""")
                .apply { ownerCookies.forEach { cookie(it) } }
        ).andExpect(status().isBadRequest())
    }
}
