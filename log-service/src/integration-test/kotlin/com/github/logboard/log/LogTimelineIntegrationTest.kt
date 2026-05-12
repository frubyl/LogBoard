package com.github.logboard.log

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.model.LogDocument
import com.github.logboard.log.model.MembershipResult
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
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
        from: Long = 0L,
        to: Long = 120_000L,
        bucketMs: Long = 60_000L,
        level: String? = null
    ): String = objectMapper.writeValueAsString(TimelineRequest(projectId, from, to, bucketMs, level))

    @Test
    fun `should return 200 with timeline buckets for authenticated project member`() {
        // Given
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-1", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-2", projectId.toString(), "ing-1", "ERROR", "msg", 2000L)
        )

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets").isArray)
            .andExpect(jsonPath("$.buckets.length()").value(2))
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
        // Given
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(MembershipResult.NotMember)

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `should return empty buckets when project has no logs`() {
        // Given
        val projectId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets").isArray)
            .andExpect(jsonPath("$.buckets.length()").value(0))
    }

    @Test
    fun `should group logs into correct time buckets`() {
        // Given
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-3", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-4", projectId.toString(), "ing-1", "INFO", "msg", 30_000L),
            LogDocument("t-id-5", projectId.toString(), "ing-1", "INFO", "msg", 61_000L)
        )

        // When
        val result = mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, from = 0L, to = 120_000L, bucketMs = 60_000L))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andReturn()

        // Then
        val body = objectMapper.readTree(result.response.contentAsString)
        val buckets = body["buckets"]
        val bucket0 = buckets.filter { it["bucket"].asLong() == 0L }.sumOf { it["count"].asLong() }
        val bucket60 = buckets.filter { it["bucket"].asLong() == 60_000L }.sumOf { it["count"].asLong() }
        bucket0 shouldBe 2L
        bucket60 shouldBe 1L
    }

    @Test
    fun `should aggregate by level within a bucket`() {
        // Given
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-6", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-7", projectId.toString(), "ing-1", "INFO", "msg", 2000L),
            LogDocument("t-id-8", projectId.toString(), "ing-1", "ERROR", "msg", 3000L)
        )

        // When
        val result = mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, from = 0L, to = 60_000L, bucketMs = 60_000L))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(2))
            .andReturn()

        // Then
        val buckets = objectMapper.readTree(result.response.contentAsString)["buckets"]
        buckets.first { it["level"].asText() == "INFO" }["count"].asLong() shouldBe 2L
        buckets.first { it["level"].asText() == "ERROR" }["count"].asLong() shouldBe 1L
    }

    @Test
    fun `should filter by time range`() {
        // Given
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-9", projectId.toString(), "ing-1", "INFO", "early", 100L),
            LogDocument("t-id-10", projectId.toString(), "ing-1", "INFO", "in range", 500L),
            LogDocument("t-id-11", projectId.toString(), "ing-1", "INFO", "late", 900L)
        )

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, from = 400L, to = 600L, bucketMs = 60_000L))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(1))
    }

    @Test
    fun `should filter by level when specified`() {
        // Given
        val projectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-12", projectId.toString(), "ing-1", "INFO", "msg", 1000L),
            LogDocument("t-id-13", projectId.toString(), "ing-1", "ERROR", "msg", 2000L),
            LogDocument("t-id-14", projectId.toString(), "ing-1", "WARN", "msg", 3000L)
        )

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId, level = "ERROR"))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(1))
            .andExpect(jsonPath("$.buckets[0].level").value("ERROR"))
            .andExpect(jsonPath("$.buckets[0].count").value(1))
    }

    @Test
    fun `should not include logs from another project`() {
        // Given
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        insertLogs(
            LogDocument("t-id-15", projectId.toString(), "ing-1", "INFO", "mine", 1000L),
            LogDocument("t-id-16", otherProjectId.toString(), "ing-2", "INFO", "other", 2000L)
        )

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(1))
    }

    @Test
    fun `should return buckets with required fields`() {
        // Given
        val projectId = UUID.randomUUID()
        insertLogs(LogDocument("t-id-17", projectId.toString(), "ing-1", "INFO", "msg", 1000L))

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets[0].bucket").exists())
            .andExpect(jsonPath("$.buckets[0].level").value("INFO"))
            .andExpect(jsonPath("$.buckets[0].count").value(1))
    }

    @Test
    fun `should return 403 when core service is unavailable and no cache`() {
        // Given
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(MembershipResult.Unavailable)

        // When & Then
        mockMvc.perform(
            post("/logs/timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(timelineRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
    }
}
