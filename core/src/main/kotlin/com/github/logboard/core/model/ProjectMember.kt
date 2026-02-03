package com.github.logboard.core.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "project_members")
@EntityListeners(EntityTimestampListener::class)
class ProjectMember(
    @EmbeddedId
    var id: ProjectMemberId? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: ProjectRole = ProjectRole.READER,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    constructor() : this(null, null, null, ProjectRole.READER, null, null)
}
