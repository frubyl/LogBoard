package com.github.logboard.core.controller

import com.github.logboard.core.dto.ApiKeyCreateRequest
import com.github.logboard.core.dto.ApiKeyCreateResponse
import com.github.logboard.core.dto.ApiKeyListItemDto
import com.github.logboard.core.model.User
import com.github.logboard.core.service.ApiKeyService
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
class ApiKeyControllerTest {

    @Mock
    private lateinit var apiKeyService: ApiKeyService

    @InjectMocks
    private lateinit var apiKeyController: ApiKeyController

    private val user = User(id = 1L, username = "testuser", password = "password")
    private val projectId: UUID = UUID.randomUUID()

    @Test
    fun `createApiKey should return 201 with api key response`() {
        val keyId = UUID.randomUUID()
        val request = ApiKeyCreateRequest(projectId = projectId, name = "My Key", expiresAt = null)
        val serviceResponse = ApiKeyCreateResponse(id = keyId, apiKey = "lb_abc123", createdAt = LocalDateTime.now())

        `when`(apiKeyService.createApiKey(request, user.id!!)).thenReturn(serviceResponse)

        val result = apiKeyController.createApiKey(request, user)

        result.statusCode shouldBe HttpStatus.CREATED
        result.body shouldBe serviceResponse
        result.body?.id shouldBe keyId
        result.body?.apiKey shouldBe "lb_abc123"
        verify(apiKeyService).createApiKey(request, user.id!!)
    }

    @Test
    fun `listApiKeys should return 200 with list of api keys`() {
        val key1 = ApiKeyListItemDto(UUID.randomUUID(), "Key 1", "testuser", null, LocalDateTime.now())
        val key2 = ApiKeyListItemDto(UUID.randomUUID(), "Key 2", "testuser", LocalDateTime.now().plusDays(30), LocalDateTime.now())

        `when`(apiKeyService.listApiKeys(projectId, user.id!!)).thenReturn(listOf(key1, key2))

        val result = apiKeyController.listApiKeys(projectId, user)

        result.statusCode shouldBe HttpStatus.OK
        result.body?.size shouldBe 2
        result.body?.get(0)?.name shouldBe "Key 1"
        result.body?.get(1)?.name shouldBe "Key 2"
        verify(apiKeyService).listApiKeys(projectId, user.id!!)
    }

    @Test
    fun `listApiKeys should return 200 with empty list when no keys`() {
        `when`(apiKeyService.listApiKeys(projectId, user.id!!)).thenReturn(emptyList())

        val result = apiKeyController.listApiKeys(projectId, user)

        result.statusCode shouldBe HttpStatus.OK
        result.body?.size shouldBe 0
    }

    @Test
    fun `revokeApiKey should return 200`() {
        val keyId = UUID.randomUUID()

        doNothing().`when`(apiKeyService).revokeApiKey(keyId, user.id!!)

        val result = apiKeyController.revokeApiKey(keyId, user)

        result.statusCode shouldBe HttpStatus.OK
        verify(apiKeyService).revokeApiKey(keyId, user.id!!)
    }
}
