package com.github.logboard.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "mySecretKeyForLogBoardApplicationWhichIsVeryLongAndSecure123456789012345",
    val accessTokenExpiration: Long = 3600000,
    val refreshTokenExpiration: Long = 86400000
)
