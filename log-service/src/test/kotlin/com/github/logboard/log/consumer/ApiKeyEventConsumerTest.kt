package com.github.logboard.log.consumer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.logboard.log.event.ApiKeyEvent
import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import com.github.logboard.log.service.LocalApiKeyCacheService
import io.kotest.core.spec.style.DescribeSpec
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import java.util.UUID

class ApiKeyEventConsumerTest : DescribeSpec({

    val repository = mock<LocalApiKeyRepository>()
    val cacheService = mock<LocalApiKeyCacheService>()
    val objectMapper = jacksonObjectMapper()
    val consumer = ApiKeyEventConsumer(repository, cacheService, objectMapper)

    beforeEach {
        reset(repository, cacheService)
    }

    describe("handle CREATED") {
        it("saves entity to repository and puts into cache") {
            val keyId = UUID.randomUUID()
            val projectId = UUID.randomUUID()
            val event = ApiKeyEvent(
                eventType = ApiKeyEvent.EventType.CREATED,
                keyId = keyId,
                projectId = projectId,
                keyHash = "hash123"
            )

            consumer.handle(objectMapper.writeValueAsString(event))

            verify(repository).save(argThat<LocalApiKey> { id == keyId && keyHash == "hash123" })
            verify(cacheService).put(any())
        }
    }

    describe("handle REVOKED") {
        it("evicts from cache and deletes from repository") {
            val keyId = UUID.randomUUID()
            val event = ApiKeyEvent(
                eventType = ApiKeyEvent.EventType.REVOKED,
                keyId = keyId,
                keyHash = "hash123"
            )

            consumer.handle(objectMapper.writeValueAsString(event))

            verify(cacheService).evict("hash123")
            verify(repository).deleteById(keyId)
        }
    }
})
