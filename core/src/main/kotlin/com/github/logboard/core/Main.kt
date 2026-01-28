package com.github.logboard.core

import com.github.logboard.core.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class LogBoardCoreApplication

fun main(args: Array<String>) {
    runApplication<LogBoardCoreApplication>(*args)
}