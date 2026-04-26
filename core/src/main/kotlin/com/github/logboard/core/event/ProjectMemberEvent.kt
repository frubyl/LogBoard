package com.github.logboard.core.event

import java.util.UUID

data class ProjectMemberEvent(
    val eventType: EventType,
    val projectId: UUID,
    val userId: Long,
    val role: String? = null
) {
    enum class EventType { ADDED, REMOVED, ROLE_CHANGED }
}
