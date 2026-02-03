package com.github.logboard.core.model

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

@Entity
@Table(name = "users")
@EntityListeners(EntityTimestampListener::class)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "username", nullable = false)
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
    var username: String = "",

    @Column(name = "password", nullable = false)
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    var password: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    constructor() : this(null, "", "", null, null)
}
