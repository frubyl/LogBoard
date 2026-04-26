package com.github.logboard.log.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.config.ApiKeyAuthFilter
import com.github.logboard.log.config.ApiKeyPrincipal
import com.github.logboard.log.dto.IngestionStatusResponse
import com.github.logboard.log.dto.LogIngestItem
import com.github.logboard.log.dto.LogIngestRequest
import com.github.logboard.log.dto.LogIngestResponse
import com.github.logboard.log.model.LogLevel
import com.github.logboard.log.service.ApiKeyValidationService
import com.github.logboard.log.service.LogIngestionService
import com.github.logboard.log.util.JwtUtil
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(LogIngestionController::class)
@Import(ApiKeyAuthFilter::class)
class LogIngestionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var logIngestionService: LogIngestionService

    @MockBean
    private lateinit var apiKeyValidationService: ApiKeyValidationService

    @MockBean
    private lateinit var jwtUtil: JwtUtil

    private val projectId = UUID.randomUUID()
    private val keyId = UUID.randomUUID()
    private val principal = ApiKeyPrincipal(keyId = keyId, projectId = projectId)
    private val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())

    @Test
    fun `ingest logs returns 200 with ingestion ID`() {
        val ingestionId = UUID.randomUUID()
        val request = LogIngestRequest(
            projectId = projectId,
            logs = listOf(LogIngestItem(level = LogLevel.INFO, message = "test", timestamp = null))
        )
        whenever(logIngestionService.ingest(any(), eq(projectId))).thenReturn(LogIngestResponse(ingestionId))

        mockMvc.post("/logs/ingest") {
            with(authentication(auth))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ingestionId") { value(ingestionId.toString()) }
        }
    }

    @Test
    fun `get status returns 200 with ingestion status`() {
        val ingestionId = UUID.randomUUID()
        val response = IngestionStatusResponse(
            ingestionId = ingestionId,
            status = "completed",
            accepted = 5,
            processed = 5,
            failed = 0,
            startedAt = LocalDateTime.now(),
            completedAt = LocalDateTime.now(),
            error = null
        )
        whenever(logIngestionService.getStatus(ingestionId)).thenReturn(response)

        mockMvc.get("/logs/ingest/$ingestionId") {
            with(authentication(auth))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("completed") }
            jsonPath("$.accepted") { value(5) }
        }
    }
}
