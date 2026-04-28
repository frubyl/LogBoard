package com.github.logboard.log.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.event.ApiKeyEvent
import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ApiKeyEventConsumer(
    private val localApiKeyRepository: LocalApiKeyRepository,
    private val objectMapper: ObjectMapper
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(ApiKeyEventConsumer::class.java)
    }

    @KafkaListener(topics = ["logboard.api-keys"], groupId = "log-service")
    fun handle(message: String) {
        val event = objectMapper.readValue(message, ApiKeyEvent::class.java)
        logger.debug("Received ApiKeyEvent: ${event.eventType} for key ${event.keyId}")

        when (event.eventType) {
            ApiKeyEvent.EventType.CREATED -> {
                val entity = LocalApiKey(
                    id = event.keyId,
                    projectId = event.projectId!!,
                    keyHash = event.keyHash!!,
                    expiresAt = event.expiresAt
                )
                localApiKeyRepository.save(entity)
                logger.info("Stored local API key ${event.keyId} for project ${event.projectId}")
            }
            ApiKeyEvent.EventType.REVOKED -> {
                localApiKeyRepository.deleteById(event.keyId)
                logger.info("Deleted local API key ${event.keyId}")
            }
        }
    }
}
