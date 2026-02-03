package com.github.logboard.core.model

import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

class EntityTimestampListener {

    @PrePersist
    fun prePersist(entity: Any) {
        when (entity) {
            is User -> {
                val now = LocalDateTime.now()
                if (entity.createdAt == null) entity.createdAt = now
                if (entity.updatedAt == null) entity.updatedAt = now
            }
            is Project -> {
                val now = LocalDateTime.now()
                if (entity.createdAt == null) entity.createdAt = now
                if (entity.updatedAt == null) entity.updatedAt = now
            }
            is ProjectMember -> {
                val now = LocalDateTime.now()
                if (entity.createdAt == null) entity.createdAt = now
                if (entity.updatedAt == null) entity.updatedAt = now
            }
        }
    }

    @PreUpdate
    fun preUpdate(entity: Any) {
        when (entity) {
            is User -> entity.updatedAt = LocalDateTime.now()
            is Project -> entity.updatedAt = LocalDateTime.now()
            is ProjectMember -> entity.updatedAt = LocalDateTime.now()
        }
    }
}
