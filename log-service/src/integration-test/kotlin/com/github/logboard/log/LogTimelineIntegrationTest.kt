package com.github.logboard.log

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.model.LogDocument
import com.github.logboard.log.model.MembershipResult
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
class LogTimelineIntegrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired
    @Qualifier("clickHouseJdbcTemplate")
    private lateinit var jdbcTemplate: JdbcTemplate

    private val userId = 77L

    private fun insertLogs(vararg docs: LogDocument) {
        val sql = "INSERT INTO logs (id, project_id, ingestion_id, level, message, timestamp) VALUES (?, ?, ?, ?, ?, ?)"
        jdbcTemplate.batchUpdate(sql, docs.map {
            arrayOf<Any>(it.id, it.projectId, it.ingestionId, it.level, it.message, it.timestamp)
        })
    }

    private fun timelineRequest(
        projectId: UUID,
        from: Instant = Instant.EPOCH,
        to: Instant = Instant.ofEpochMilli(120_000L),
        level: List<String>? = null,
        message: String? = null
    ): String = objectMapper.writeValueAsString(TimelineRequest(projectId, from, to, level, message))

    @Test
    fun `should return 200 with timeline items for authenticated project member`() {
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-1", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-2", projectId.toString(), "ing-1", "ERROR", "msg", 61_000L)
        )

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `should return 401 when request has no authentication cookie`() {
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(UUID.randomUUID()))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 403 when user is not a member of the project`() {
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(MembershipResult.NotMember)

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `should return empty array when project has no logs`() {
        val projectId = UUID.randomUUID()

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `should group logs into correct time buckets`() {
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-3", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-4", projectId.toString(), "ing-1", "INFO", "msg", 30_000L),
            LogDocument("t-id-5", projectId.toString(), "ing-1", "INFO", "msg", 61_000L)
        )

        val result = mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, from = Instant.EPOCH, to = Instant.ofEpochMilli(120_000L)))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsString)
        assertEquals(2, body.size())
        val totalCounts = body.map { it["totalCount"].asLong() }.sortedDescending()
        assertEquals(listOf(2L, 1L), totalCounts)
    }

    @Test
    fun `should aggregate error and warn counts per bucket`() {
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-6", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-7", projectId.toString(), "ing-1", "INFO", "msg", 2000L),
            LogDocument("t-id-8", projectId.toString(), "ing-1", "ERROR", "msg", 3000L)
        )

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, from = Instant.EPOCH, to = Instant.ofEpochMilli(60_000L)))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].totalCount").value(3))
            .andExpect(jsonPath("$[0].errorCount").value(1))
            .andExpect(jsonPath("$[0].warnCount").value(0))
    }

    @Test
    fun `should filter by time range`() {
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-9", projectId.toString(), "ing-1", "INFO", "early", 100L),
            LogDocument("t-id-10", projectId.toString(), "ing-1", "INFO", "in range", 500L),
            LogDocument("t-id-11", projectId.toString(), "ing-1", "INFO", "late", 900L)
        )

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, from = Instant.ofEpochMilli(400L), to = Instant.ofEpochMilli(600L)))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `should filter by level when specified`() {
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-12", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-13", projectId.toString(), "ing-1", "ERROR", "msg", 2000L),
            LogDocument("t-id-14", projectId.toString(), "ing-1", "WARN", "msg", 3000L)
        )

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, level = listOf("ERROR")))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].totalCount").value(1))
            .andExpect(jsonPath("$[0].errorCount").value(1))
    }

    @Test
    fun `should not include logs from another project`() {
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-15", projectId.toString(), "ing-1", "INFO", "mine", 1000L),
            LogDocument("t-id-16", otherProjectId.toString(), "ing-2", "INFO", "other", 2000L)
        )

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `should return items with required fields`() {
        val projectId = UUID.randomUUID()
        insertLogs(LogDocument("t-id-17", projectId.toString(), "ing-1", "INFO", "msg", 1000L))

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].timestamp").exists())
            .andExpect(jsonPath("$[0].totalCount").value(1))
            .andExpect(jsonPath("$[0].errorCount").value(0))
            .andExpect(jsonPath("$[0].warnCount").value(0))
    }

    @Test
    fun `should return 403 when core service is unavailable and no cache`() {
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(MembershipResult.Unavailable)

        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
    }
}
