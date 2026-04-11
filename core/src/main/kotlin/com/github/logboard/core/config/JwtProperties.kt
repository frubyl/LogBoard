package com.github.logboard.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "mySecretKeyForLogBoardApplicationWhichIsVeryLongAndSecure123456789012345",
    val accessTokenExpiration: Long = 900000,        // 15 минут в миллисекундах
    val refreshTokenExpiration: Long = 604800000,    // 7 дней в миллисекундах
    val accessTokenCookieMaxAge: Int = 900,          // 15 минут в секундах
    val refreshTokenCookieMaxAge: Int = 604800       // 7 дней в секундах
)
