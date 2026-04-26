package com.github.logboard.log.controller

import com.github.logboard.log.config.ApiKeyPrincipal
import com.github.logboard.log.dto.IngestionStatusResponse
import com.github.logboard.log.dto.LogIngestRequest
import com.github.logboard.log.dto.LogIngestResponse
import com.github.logboard.log.service.LogIngestionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/logs/ingest")
class LogIngestionController(private val logIngestionService: LogIngestionService) {

    @PostMapping
    fun ingestLogs(
        @Valid @RequestBody request: LogIngestRequest,
        authentication: Authentication
    ): ResponseEntity<LogIngestResponse> {
        val principal = authentication.principal as ApiKeyPrincipal
        val response = logIngestionService.ingest(request, principal.projectId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{ingestionId}")
    fun getIngestionStatus(
        @PathVariable ingestionId: UUID,
        authentication: Authentication
    ): ResponseEntity<IngestionStatusResponse> {
        val response = logIngestionService.getStatus(ingestionId)
        return ResponseEntity.ok(response)
    }
}
