package com.github.logboard.log.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class LocalProjectMemberId(
    val projectId: UUID = UUID.randomUUID(),
    val userId: Long = 0
) : Serializable

@Entity
@Table(name = "local_project_members")
@IdClass(LocalProjectMemberId::class)
class LocalProjectMember(
    @Id
    @Column(name = "project_id")
    val projectId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: Long,

    @Column(name = "role", nullable = false)
    var role: String
)
