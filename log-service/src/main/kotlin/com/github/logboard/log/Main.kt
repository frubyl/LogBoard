package com.github.logboard.log

import com.github.logboard.log.config.ApiKeyProperties
import com.github.logboard.log.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(JwtProperties::class, ApiKeyProperties::class)
class LogBoardLogApplication

fun main(args: Array<String>) {
    runApplication<LogBoardLogApplication>(*args)
}
