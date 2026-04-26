package com.github.logboard.log.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "ingestion_status")
class IngestionStatus(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var state: IngestionState = IngestionState.PENDING,

    @Column(name = "accepted", nullable = false)
    var accepted: Int = 0,

    @Column(name = "processed", nullable = false)
    var processed: Int = 0,

    @Column(name = "failed", nullable = false)
    var failed: Int = 0,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "error", columnDefinition = "TEXT")
    var error: String? = null
)
