package com.github.logboard.log.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "api-key")
data class ApiKeyProperties(
    val hmacSecret: String
)
