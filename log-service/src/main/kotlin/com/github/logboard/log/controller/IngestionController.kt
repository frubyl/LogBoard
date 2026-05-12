package com.github.logboard.log.controller

import com.github.logboard.log.dto.IngestRequest
import com.github.logboard.log.dto.IngestResponse
import com.github.logboard.log.security.ApiKeyAuthentication
import com.github.logboard.log.service.IngestionService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/logs")
class IngestionController(
    private val ingestionService: IngestionService
) {

    @PostMapping("/ingest")
    fun ingest(
        @AuthenticationPrincipal projectId: UUID,
        @RequestBody request: IngestRequest
    ): ResponseEntity<IngestResponse> {
        val ingestionId = ingestionService.ingest(projectId, request.entries)
        return ResponseEntity.accepted().body(IngestResponse(ingestionId))
    }
}
