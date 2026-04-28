package com.github.logboard.core.event

import java.time.LocalDateTime
import java.util.UUID

data class ApiKeyEvent(
    val eventType: EventType,
    val keyId: UUID,
    val projectId: UUID? = null,
    val keyHash: String? = null,
    val expiresAt: LocalDateTime? = null
) {
    enum class EventType { CREATED, REVOKED }
}
