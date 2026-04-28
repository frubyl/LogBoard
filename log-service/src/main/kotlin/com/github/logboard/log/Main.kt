package com.github.logboard.log

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LogBoardLogApplication

fun main(args: Array<String>) {
    runApplication<LogBoardLogApplication>(*args)
}
