package com.github.logboard.core.service

import com.github.logboard.core.config.ApiKeyProperties
import com.github.logboard.core.dto.ApiKeyCreateRequest
import com.github.logboard.core.dto.ApiKeyCreateResponse
import com.github.logboard.core.dto.ApiKeyListItemDto
import com.github.logboard.core.event.ApiKeyEvent
import com.github.logboard.core.event.KafkaTopics
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.ApiKey
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.repository.ApiKeyRepository
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val projectRepository: ProjectRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userService: UserService,
    private val apiKeyProperties: ApiKeyProperties,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ApiKeyService::class.java)
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    @Transactional
    fun createApiKey(request: ApiKeyCreateRequest, userId: Long): ApiKeyCreateResponse {
        val projectId = request.projectId!!
        logger.info("Creating API key '${request.name}' for project $projectId by user id: $userId")

        val project = projectRepository.findById(projectId)
            .orElseThrow { NotFoundException("Project not found with id: $projectId") }

        val member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (member.role != ProjectRole.OWNER && member.role != ProjectRole.ADMIN) {
            throw ForbiddenException("Only project OWNER or ADMIN can manage API keys")
        }

        val user = userService.loadUserById(userId)
        val rawKey = "lb_${UUID.randomUUID().toString().replace("-", "")}"
        val keyHash = hmacSha256(rawKey)

        val apiKey = ApiKey(
            project = project,
            name = request.name!!,
            keyHash = keyHash,
            createdBy = user,
            expiresAt = request.expiresAt
        )

        val saved = apiKeyRepository.save(apiKey)
        logger.info("API key created with id: ${saved.id} for project $projectId")

        kafkaTemplate.send(
            KafkaTopics.API_KEYS,
            saved.id.toString(),
            ApiKeyEvent(
                eventType = ApiKeyEvent.EventType.CREATED,
                keyId = saved.id!!,
                projectId = projectId,
                keyHash = keyHash,
                expiresAt = request.expiresAt
            )
        )

        return ApiKeyCreateResponse(
            id = saved.id!!,
            apiKey = rawKey,
            createdAt = saved.createdAt!!
        )
    }

    fun listApiKeys(projectId: UUID, userId: Long): List<ApiKeyListItemDto> {
        logger.info("Listing API keys for project $projectId by user id: $userId")

        if (!projectRepository.existsById(projectId)) {
            throw NotFoundException("Project not found with id: $projectId")
        }

        val member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (member.role != ProjectRole.OWNER && member.role != ProjectRole.ADMIN) {
            throw ForbiddenException("Only project OWNER or ADMIN can view API keys")
        }

        return apiKeyRepository.findAllByProjectId(projectId).map { key ->
            ApiKeyListItemDto(
                id = key.id!!,
                name = key.name,
                createdBy = key.createdBy?.username ?: "Unknown",
                expiresAt = key.expiresAt,
                createdAt = key.createdAt!!
            )
        }
    }

    @Transactional
    fun revokeApiKey(keyId: UUID, userId: Long) {
        logger.info("Revoking API key $keyId by user id: $userId")

        val apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow { NotFoundException("API key not found with id: $keyId") }

        val projectId = apiKey.project?.id
            ?: throw NotFoundException("Project not found for API key $keyId")

        val member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ForbiddenException("User is not a member of this project")

        if (member.role != ProjectRole.OWNER && member.role != ProjectRole.ADMIN) {
            throw ForbiddenException("Only project OWNER or ADMIN can revoke API keys")
        }

        val keyHash = apiKey.keyHash
        apiKeyRepository.delete(apiKey)
        logger.info("API key $keyId revoked successfully")

        kafkaTemplate.send(
            KafkaTopics.API_KEYS,
            keyId.toString(),
            ApiKeyEvent(eventType = ApiKeyEvent.EventType.REVOKED, keyId = keyId, keyHash = keyHash)
        )
    }

    private fun hmacSha256(value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(apiKeyProperties.hmacSecret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKey)
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
