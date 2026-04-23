package com.github.logboard.core.service

import com.github.logboard.core.config.ApiKeyProperties
import com.github.logboard.core.dto.ApiKeyCreateRequest
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.ApiKey
import com.github.logboard.core.model.Project
import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.model.User
import com.github.logboard.core.repository.ApiKeyRepository
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
class ApiKeyServiceTest {

    @Mock
    private lateinit var apiKeyRepository: ApiKeyRepository

    @Mock
    private lateinit var projectRepository: ProjectRepository

    @Mock
    private lateinit var projectMemberRepository: ProjectMemberRepository

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var apiKeyProperties: ApiKeyProperties

    @InjectMocks
    private lateinit var apiKeyService: ApiKeyService

    private lateinit var user: User
    private lateinit var project: Project
    private lateinit var ownerMember: ProjectMember
    private lateinit var adminMember: ProjectMember
    private lateinit var readerMember: ProjectMember

    @BeforeEach
    fun setUp() {
        user = User(id = 1L, username = "testuser", password = "encodedPassword",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        project = Project(id = UUID.randomUUID(), name = "Test Project",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        ownerMember = ProjectMember(
            id = ProjectMemberId(project.id, user.id),
            project = project, user = user, role = ProjectRole.OWNER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        adminMember = ProjectMember(
            id = ProjectMemberId(project.id, user.id),
            project = project, user = user, role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        readerMember = ProjectMember(
            id = ProjectMemberId(project.id, user.id),
            project = project, user = user, role = ProjectRole.READER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

    }

    // --- createApiKey ---

    @Test
    fun `createApiKey should create and return response when user is OWNER`() {
        val projectId = project.id!!
        val request = ApiKeyCreateRequest(projectId = projectId, name = "My Key", expiresAt = null)
        val savedKey = ApiKey(id = UUID.randomUUID(), project = project, name = "My Key",
            keyHash = "somehash", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(apiKeyProperties.hmacSecret).thenReturn("test-hmac-secret-for-unit-tests-only")
        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(ownerMember)
        `when`(userService.loadUserById(user.id!!)).thenReturn(user)
        `when`(apiKeyRepository.save(any(ApiKey::class.java))).thenReturn(savedKey)

        val result = apiKeyService.createApiKey(request, user.id!!)

        result.id shouldBe savedKey.id
        result.apiKey shouldStartWith "lb_"
        verify(apiKeyRepository).save(any(ApiKey::class.java))
    }

    @Test
    fun `createApiKey should create and return response when user is ADMIN`() {
        val projectId = project.id!!
        val request = ApiKeyCreateRequest(projectId = projectId, name = "Admin Key", expiresAt = null)
        val savedKey = ApiKey(id = UUID.randomUUID(), project = project, name = "Admin Key",
            keyHash = "somehash", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(apiKeyProperties.hmacSecret).thenReturn("test-hmac-secret-for-unit-tests-only")
        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(adminMember)
        `when`(userService.loadUserById(user.id!!)).thenReturn(user)
        `when`(apiKeyRepository.save(any(ApiKey::class.java))).thenReturn(savedKey)

        val result = apiKeyService.createApiKey(request, user.id!!)

        result.id shouldBe savedKey.id
        verify(apiKeyRepository).save(any(ApiKey::class.java))
    }

    @Test
    fun `createApiKey should throw NotFoundException when project not found`() {
        val projectId = UUID.randomUUID()
        val request = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.empty())

        val ex = shouldThrow<NotFoundException> { apiKeyService.createApiKey(request, user.id!!) }
        ex.message shouldBe "Project not found with id: $projectId"
        verify(apiKeyRepository, never()).save(any())
    }

    @Test
    fun `createApiKey should throw ForbiddenException when user is not a project member`() {
        val projectId = project.id!!
        val request = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> { apiKeyService.createApiKey(request, user.id!!) }
        ex.message shouldBe "User is not a member of this project"
        verify(apiKeyRepository, never()).save(any())
    }

    @Test
    fun `createApiKey should throw ForbiddenException when user has READER role`() {
        val projectId = project.id!!
        val request = ApiKeyCreateRequest(projectId = projectId, name = "Key", expiresAt = null)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(readerMember)

        val ex = shouldThrow<ForbiddenException> { apiKeyService.createApiKey(request, user.id!!) }
        ex.message shouldBe "Only project OWNER or ADMIN can manage API keys"
        verify(apiKeyRepository, never()).save(any())
    }

    // --- listApiKeys ---

    @Test
    fun `listApiKeys should return list of keys when user is OWNER`() {
        val projectId = project.id!!
        val key1 = ApiKey(id = UUID.randomUUID(), project = project, name = "Key 1",
            keyHash = "hash1", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        val key2 = ApiKey(id = UUID.randomUUID(), project = project, name = "Key 2",
            keyHash = "hash2", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(ownerMember)
        `when`(apiKeyRepository.findAllByProjectId(projectId)).thenReturn(listOf(key1, key2))

        val result = apiKeyService.listApiKeys(projectId, user.id!!)

        result.size shouldBe 2
        result[0].name shouldBe "Key 1"
        result[1].name shouldBe "Key 2"
        result[0].createdBy shouldBe user.username
    }

    @Test
    fun `listApiKeys should return empty list when project has no keys`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(ownerMember)
        `when`(apiKeyRepository.findAllByProjectId(projectId)).thenReturn(emptyList())

        val result = apiKeyService.listApiKeys(projectId, user.id!!)

        result.size shouldBe 0
    }

    @Test
    fun `listApiKeys should throw NotFoundException when project not found`() {
        val projectId = UUID.randomUUID()

        `when`(projectRepository.existsById(projectId)).thenReturn(false)

        val ex = shouldThrow<NotFoundException> { apiKeyService.listApiKeys(projectId, user.id!!) }
        ex.message shouldBe "Project not found with id: $projectId"
    }

    @Test
    fun `listApiKeys should throw ForbiddenException when user is not a project member`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> { apiKeyService.listApiKeys(projectId, user.id!!) }
        ex.message shouldBe "User is not a member of this project"
    }

    @Test
    fun `listApiKeys should throw ForbiddenException when user has READER role`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, user.id!!)).thenReturn(readerMember)

        val ex = shouldThrow<ForbiddenException> { apiKeyService.listApiKeys(projectId, user.id!!) }
        ex.message shouldBe "Only project OWNER or ADMIN can view API keys"
    }

    // --- revokeApiKey ---

    @Test
    fun `revokeApiKey should delete key when user is OWNER`() {
        val keyId = UUID.randomUUID()
        val apiKey = ApiKey(id = keyId, project = project, name = "Key",
            keyHash = "hash", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey))
        `when`(projectMemberRepository.findByProjectIdAndUserId(project.id!!, user.id!!)).thenReturn(ownerMember)
        doNothing().`when`(apiKeyRepository).delete(apiKey)

        apiKeyService.revokeApiKey(keyId, user.id!!)

        verify(apiKeyRepository).delete(apiKey)
    }

    @Test
    fun `revokeApiKey should delete key when user is ADMIN`() {
        val keyId = UUID.randomUUID()
        val apiKey = ApiKey(id = keyId, project = project, name = "Key",
            keyHash = "hash", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey))
        `when`(projectMemberRepository.findByProjectIdAndUserId(project.id!!, user.id!!)).thenReturn(adminMember)
        doNothing().`when`(apiKeyRepository).delete(apiKey)

        apiKeyService.revokeApiKey(keyId, user.id!!)

        verify(apiKeyRepository).delete(apiKey)
    }

    @Test
    fun `revokeApiKey should throw NotFoundException when api key not found`() {
        val keyId = UUID.randomUUID()

        `when`(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty())

        val ex = shouldThrow<NotFoundException> { apiKeyService.revokeApiKey(keyId, user.id!!) }
        ex.message shouldBe "API key not found with id: $keyId"
        verify(apiKeyRepository, never()).delete(any())
    }

    @Test
    fun `revokeApiKey should throw ForbiddenException when user is not a project member`() {
        val keyId = UUID.randomUUID()
        val apiKey = ApiKey(id = keyId, project = project, name = "Key",
            keyHash = "hash", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey))
        `when`(projectMemberRepository.findByProjectIdAndUserId(project.id!!, user.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> { apiKeyService.revokeApiKey(keyId, user.id!!) }
        ex.message shouldBe "User is not a member of this project"
        verify(apiKeyRepository, never()).delete(any())
    }

    @Test
    fun `revokeApiKey should throw ForbiddenException when user has READER role`() {
        val keyId = UUID.randomUUID()
        val apiKey = ApiKey(id = keyId, project = project, name = "Key",
            keyHash = "hash", createdBy = user,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        `when`(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey))
        `when`(projectMemberRepository.findByProjectIdAndUserId(project.id!!, user.id!!)).thenReturn(readerMember)

        val ex = shouldThrow<ForbiddenException> { apiKeyService.revokeApiKey(keyId, user.id!!) }
        ex.message shouldBe "Only project OWNER or ADMIN can revoke API keys"
        verify(apiKeyRepository, never()).delete(any())
    }
}
