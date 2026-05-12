package com.github.logboard.log.security

data class AuthenticatedUser(
    val userId: Long,
    val token: String
)
