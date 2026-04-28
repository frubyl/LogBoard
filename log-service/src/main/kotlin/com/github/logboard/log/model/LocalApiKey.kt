package com.github.logboard.log.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "local_api_keys")
class LocalApiKey(
    @Id
    val id: UUID,

    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(name = "key_hash", nullable = false, unique = true)
    val keyHash: String,

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null
)
