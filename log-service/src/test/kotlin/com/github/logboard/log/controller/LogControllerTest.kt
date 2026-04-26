package com.github.logboard.log.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.config.JwtAuthFilter
import com.github.logboard.log.config.JwtPrincipal
import com.github.logboard.log.dto.LogEntry
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.dto.LogTimelineRequest
import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.model.LogLevel
import com.github.logboard.log.service.ApiKeyValidationService
import com.github.logboard.log.service.LogSearchService
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
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(LogController::class)
@Import(JwtAuthFilter::class)
class LogControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var logSearchService: LogSearchService

    @MockBean
    private lateinit var jwtUtil: JwtUtil

    @MockBean
    private lateinit var apiKeyValidationService: ApiKeyValidationService

    private val userId = 1L
    private val principal = JwtPrincipal(userId = userId)
    private val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())

    @Test
    fun `search returns 200 with logs`() {
        val projectId = UUID.randomUUID()
        val now = LocalDateTime.now()
        val request = LogSearchRequest(projectId = projectId, from = now.minusHours(1), to = now)
        val response = LogSearchResponse(
            logs = listOf(LogEntry(level = LogLevel.INFO, message = "hello", timestamp = now)),
            nextCursor = null,
            totalCount = 1L
        )
        whenever(logSearchService.search(any(), eq(userId))).thenReturn(response)

        mockMvc.post("/logs/search") {
            with(authentication(auth))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalCount") { value(1) }
            jsonPath("$.logs[0].message") { value("hello") }
        }
    }

    @Test
    fun `timeline returns 200 with buckets`() {
        val projectId = UUID.randomUUID()
        val now = LocalDateTime.now()
        val request = LogTimelineRequest(projectId = projectId, from = now.minusHours(1), to = now)
        val response = listOf(TimelineItem(timestamp = now, totalCount = 10, errorCount = 2, warnCount = 1))
        whenever(logSearchService.timeline(any(), eq(userId))).thenReturn(response)

        mockMvc.post("/logs/timeline") {
            with(authentication(auth))
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].totalCount") { value(10) }
        }
    }
}
