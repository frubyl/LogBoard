package com.github.logboard.log.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "raw_logs")
class RawLog(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "project_id", nullable = false)
    val projectId: UUID,

    @Column(name = "ingestion_id", nullable = false)
    val ingestionId: UUID,

    @Column(nullable = false, length = 16)
    val level: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val message: String,

    @Column(nullable = false)
    val timestamp: Long,

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
