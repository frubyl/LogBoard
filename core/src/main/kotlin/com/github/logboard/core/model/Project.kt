package com.github.logboard.core.model

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "projects")
@EntityListeners(EntityTimestampListener::class)
class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @Column(name = "name", nullable = false, length = 100)
    @field:NotBlank(message = "Project name is required")
    @field:Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    var name: String = "",

    @Column(name = "description", length = 1000)
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    var description: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var members: MutableList<ProjectMember> = mutableListOf()
) {
    constructor() : this(null, "", null, null, null, mutableListOf())
}
