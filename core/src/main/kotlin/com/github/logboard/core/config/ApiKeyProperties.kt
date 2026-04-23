package com.github.logboard.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "api-key")
data class ApiKeyProperties(
    val hmacSecret: String
)
