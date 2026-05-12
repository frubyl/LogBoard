package com.github.logboard.log.model

data class LogDocument(
    val id: String,
    val projectId: String,
    val ingestionId: String,
    val level: String,
    val message: String,
    val timestamp: Long
)
