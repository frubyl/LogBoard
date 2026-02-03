package com.github.logboard.core.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.*

@Embeddable
data class ProjectMemberId(
    @Column(name = "project_id")
    val projectId: UUID? = null,

    @Column(name = "user_id")
    val userId: Long? = null
) : Serializable {
    constructor() : this(null, null)
}
