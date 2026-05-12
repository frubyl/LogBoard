package com.github.logboard.log.dto

data class LogEntry(
    val id: String,
    val ingestionId: String,
    val level: String,
    val message: String,
    val timestamp: Long
)
