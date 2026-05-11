package com.github.logboard.log.dto

data class TimelineBucket(
    val bucket: Long,
    val level: String,
    val count: Long
)
