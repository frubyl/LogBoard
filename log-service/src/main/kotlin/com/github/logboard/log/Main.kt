package com.github.logboard.log

import com.github.logboard.log.config.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class LogBoardLogApplication

fun main(args: Array<String>) {
    runApplication<LogBoardLogApplication>(*args)
}
