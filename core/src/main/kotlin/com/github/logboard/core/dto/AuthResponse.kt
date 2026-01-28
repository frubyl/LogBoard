package com.github.logboard.core.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)
