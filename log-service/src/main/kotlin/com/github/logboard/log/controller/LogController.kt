package com.github.logboard.log.controller

import com.github.logboard.log.config.JwtPrincipal
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.dto.LogTimelineRequest
import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.service.LogSearchService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/logs")
class LogController(private val logSearchService: LogSearchService) {

    @PostMapping("/search")
    fun searchLogs(
        @Valid @RequestBody request: LogSearchRequest,
        authentication: Authentication
    ): ResponseEntity<LogSearchResponse> {
        val principal = authentication.principal as JwtPrincipal
        val response = logSearchService.search(request, principal.userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/timeline")
    fun getTimeline(
        @Valid @RequestBody request: LogTimelineRequest,
        authentication: Authentication
    ): ResponseEntity<List<TimelineItem>> {
        val principal = authentication.principal as JwtPrincipal
        val response = logSearchService.timeline(request, principal.userId)
        return ResponseEntity.ok(response)
    }
}
