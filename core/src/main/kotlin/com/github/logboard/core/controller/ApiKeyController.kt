package com.github.logboard.core.controller

import com.github.logboard.core.dto.ApiKeyCreateRequest
import com.github.logboard.core.dto.ApiKeyCreateResponse
import com.github.logboard.core.dto.ApiKeyListItemDto
import com.github.logboard.core.model.User
import com.github.logboard.core.service.ApiKeyService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api-keys")
class ApiKeyController(
    private val apiKeyService: ApiKeyService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ApiKeyController::class.java)
    }

    @PostMapping
    fun createApiKey(
        @Valid @RequestBody request: ApiKeyCreateRequest,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<ApiKeyCreateResponse> {
        logger.info("Creating API key '${request.name}' for project ${request.projectId} by user: ${user.username}")
        val response = apiKeyService.createApiKey(request, user.id!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listApiKeys(
        @RequestParam projectId: UUID,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<List<ApiKeyListItemDto>> {
        logger.info("Listing API keys for project $projectId by user: ${user.username}")
        val keys = apiKeyService.listApiKeys(projectId, user.id!!)
        return ResponseEntity.ok(keys)
    }

    @DeleteMapping("/{keyId}")
    fun revokeApiKey(
        @PathVariable keyId: UUID,
        @AuthenticationPrincipal user: User
    ): ResponseEntity<Void> {
        logger.info("Revoking API key $keyId by user: ${user.username}")
        apiKeyService.revokeApiKey(keyId, user.id!!)
        return ResponseEntity.ok().build()
    }
}
