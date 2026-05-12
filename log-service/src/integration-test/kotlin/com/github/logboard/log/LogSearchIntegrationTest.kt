package com.github.logboard.log

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
class LogSearchIntegrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var elasticsearchOperations: ElasticsearchOperations

    private val userId = 42L
    private val defaultFrom = Instant.EPOCH
    private val defaultTo = Instant.parse("2099-01-01T00:00:00Z")

    @BeforeEach
    fun setUpIndex() {
        val indexOps = elasticsearchOperations.indexOps(LogDocumentEs::class.java)
        if (!indexOps.exists()) {
            indexOps.createWithMapping()
        }
    }

    private fun indexDoc(doc: LogDocumentEs) {
        val query = IndexQueryBuilder().withId(doc.id).withObject(doc).build()
        elasticsearchOperations.index(query, IndexCoordinates.of("logs"))
        elasticsearchOperations.indexOps(IndexCoordinates.of("logs")).refresh()
    }

    private fun searchRequest(
        projectId: UUID,
        from: Instant = defaultFrom,
        to: Instant = defaultTo,
        level: List<String>? = null,
        message: String? = null,
        size: Int = 50,
        cursor: Instant? = null
    ): String = objectMapper.writeValueAsString(
        LogSearchRequest(projectId = projectId, from = from, to = to, level = level, message = message, size = size, cursor = cursor)
    )

    @Test
    fun `should return 200 with matching logs for authenticated project member`() {
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-1-$projectId", projectId.toString(), "ing-1", "INFO", "application started", 1000L))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs").isArray)
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].level").value("INFO"))
            .andExpect(jsonPath("$.totalCount").value(1))
    }

    @Test
    fun `should return 401 when request has no authentication cookie`() {
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(UUID.randomUUID()))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return 403 when user is not a member of the project`() {
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(com.github.logboard.log.model.MembershipResult.NotMember)

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `should return 403 when core service is unavailable and no cache`() {
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(com.github.logboard.log.model.MembershipResult.Unavailable)

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `should return empty list when project has no logs`() {
        val projectId = UUID.randomUUID()

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs").isArray)
            .andExpect(jsonPath("$.logs.length()").value(0))
            .andExpect(jsonPath("$.totalCount").value(0))
    }

    @Test
    fun `should filter logs by level`() {
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-lvl-1-$projectId", projectId.toString(), "ing-1", "INFO", "info message", 1000L))
        indexDoc(LogDocumentEs("idx-lvl-2-$projectId", projectId.toString(), "ing-1", "ERROR", "error message", 2000L))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId, level = listOf("ERROR")))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].level").value("ERROR"))
            .andExpect(jsonPath("$.totalCount").value(1))
    }

    @Test
    fun `should filter logs by message case-insensitively`() {
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-msg-1-$projectId", projectId.toString(), "ing-1", "INFO", "Connection timeout occurred", 1000L))
        indexDoc(LogDocumentEs("idx-msg-2-$projectId", projectId.toString(), "ing-1", "ERROR", "NullPointerException", 2000L))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId, message = "TIMEOUT"))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].message").value("Connection timeout occurred"))
    }

    @Test
    fun `should filter logs by timestamp range`() {
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-ts-1-$projectId", projectId.toString(), "ing-1", "INFO", "early", 100L))
        indexDoc(LogDocumentEs("idx-ts-2-$projectId", projectId.toString(), "ing-1", "INFO", "middle", 500L))
        indexDoc(LogDocumentEs("idx-ts-3-$projectId", projectId.toString(), "ing-1", "INFO", "late", 900L))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId, from = Instant.ofEpochMilli(200L), to = Instant.ofEpochMilli(800L)))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].message").value("middle"))
    }

    @Test
    fun `should not return logs from another project`() {
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-prj-1-$projectId", projectId.toString(), "ing-1", "INFO", "mine", 1000L))
        indexDoc(LogDocumentEs("idx-prj-2-$otherProjectId", otherProjectId.toString(), "ing-2", "INFO", "other", 2000L))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs.length()").value(1))
            .andExpect(jsonPath("$.logs[0].message").value("mine"))
    }

    @Test
    fun `should return results sorted by timestamp descending`() {
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-sort-1-$projectId", projectId.toString(), "ing-1", "INFO", "first", 1000L))
        indexDoc(LogDocumentEs("idx-sort-2-$projectId", projectId.toString(), "ing-1", "INFO", "third", 3000L))
        indexDoc(LogDocumentEs("idx-sort-3-$projectId", projectId.toString(), "ing-1", "INFO", "second", 2000L))

        val result = mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsString)
        val messages = body["logs"].map { it["message"].asText() }
        assertEquals(listOf("third", "second", "first"), messages)
    }

    @Test
    fun `should paginate results with cursor`() {
        val projectId = UUID.randomUUID()
        repeat(5) { i ->
            indexDoc(LogDocumentEs("idx-page-$i-$projectId", projectId.toString(), "ing-1", "INFO", "msg $i", i.toLong() * 1000 + 1000))
        }

        val result1 = mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId, size = 2))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs.length()").value(2))
            .andExpect(jsonPath("$.totalCount").value(5))
            .andExpect(jsonPath("$.nextCursor").exists())
            .andReturn()

        val body1 = objectMapper.readTree(result1.response.contentAsString)
        val cursor = objectMapper.treeToValue(body1["nextCursor"], Instant::class.java)

        val result2 = mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId, size = 2, cursor = cursor))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs.length()").value(2))
            .andReturn()

        val body2 = objectMapper.readTree(result2.response.contentAsString)
        val timestamps1 = body1["logs"].map { it["timestamp"].asText() }.toSet()
        val timestamps2 = body2["logs"].map { it["timestamp"].asText() }.toSet()
        assertTrue(timestamps1.intersect(timestamps2).isEmpty())
    }

    @Test
    fun `should cap page size at 500`() {
        val projectId = UUID.randomUUID()

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId, size = 9999))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs").isArray)
    }

    @Test
    fun `should return 401 when token is invalid`() {
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(UUID.randomUUID()))
                .cookie(Cookie("access_token", "invalid.jwt.token"))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `should return response with all required fields`() {
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-fields-$projectId", projectId.toString(), "ing-check", "WARN", "check fields", 5000L))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.logs[0].level").value("WARN"))
            .andExpect(jsonPath("$.logs[0].message").value("check fields"))
            .andExpect(jsonPath("$.logs[0].timestamp").exists())
            .andExpect(jsonPath("$.totalCount").exists())
    }
}
