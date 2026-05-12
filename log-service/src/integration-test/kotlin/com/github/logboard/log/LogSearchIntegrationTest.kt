package com.github.logboard.log

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.model.LogDocumentEs
import com.github.logboard.log.model.MembershipResult
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
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
import java.util.UUID

@SpringBootTest
class LogSearchIntegrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var elasticsearchOperations: ElasticsearchOperations

    private val userId = 42L

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

    private fun searchRequest(projectId: UUID): String =
        objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId))

    @Test
    fun `should return 200 with matching logs for authenticated project member`() {
        // Given
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-1-${projectId}", projectId.toString(), "ing-1", "INFO", "application started", 1000L))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].level").value("INFO"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50))
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
        // Given
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(MembershipResult.NotMember)

        // When & Then
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
        // Given
        val projectId = UUID.randomUUID()
        given(coreServiceClient.getMembership(eq(projectId), any())).willReturn(MembershipResult.Unavailable)

        // When & Then
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
        // Given
        val projectId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `should filter logs by level`() {
        // Given
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-lvl-1-${projectId}", projectId.toString(), "ing-1", "INFO", "info message", 1000L))
        indexDoc(LogDocumentEs("idx-lvl-2-${projectId}", projectId.toString(), "ing-1", "ERROR", "error message", 2000L))

        val request = objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId, level = "ERROR"))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].level").value("ERROR"))
            .andExpect(jsonPath("$.total").value(1))
    }

    @Test
    fun `should filter logs by message case-insensitively`() {
        // Given
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-msg-1-${projectId}", projectId.toString(), "ing-1", "INFO", "Connection timeout occurred", 1000L))
        indexDoc(LogDocumentEs("idx-msg-2-${projectId}", projectId.toString(), "ing-1", "ERROR", "NullPointerException", 2000L))

        val request = objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId, message = "TIMEOUT"))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].message").value("Connection timeout occurred"))
    }

    @Test
    fun `should filter logs by timestamp range`() {
        // Given
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-ts-1-${projectId}", projectId.toString(), "ing-1", "INFO", "early", 100L))
        indexDoc(LogDocumentEs("idx-ts-2-${projectId}", projectId.toString(), "ing-1", "INFO", "middle", 500L))
        indexDoc(LogDocumentEs("idx-ts-3-${projectId}", projectId.toString(), "ing-1", "INFO", "late", 900L))

        val request = objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId, from = 200L, to = 800L))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].message").value("middle"))
    }

    @Test
    fun `should not return logs from another project`() {
        // Given
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-prj-1-${projectId}", projectId.toString(), "ing-1", "INFO", "mine", 1000L))
        indexDoc(LogDocumentEs("idx-prj-2-${otherProjectId}", otherProjectId.toString(), "ing-2", "INFO", "other", 2000L))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].message").value("mine"))
    }

    @Test
    fun `should return results sorted by timestamp descending`() {
        // Given
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-sort-1-${projectId}", projectId.toString(), "ing-1", "INFO", "first", 1000L))
        indexDoc(LogDocumentEs("idx-sort-2-${projectId}", projectId.toString(), "ing-1", "INFO", "third", 3000L))
        indexDoc(LogDocumentEs("idx-sort-3-${projectId}", projectId.toString(), "ing-1", "INFO", "second", 2000L))

        // When
        val result = mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andReturn()

        // Then
        val body = objectMapper.readTree(result.response.contentAsString)
        val timestamps = body["items"].map { it["timestamp"].asLong() }
        timestamps shouldBe listOf(3000L, 2000L, 1000L)
    }

    @Test
    fun `should paginate results correctly`() {
        // Given
        val projectId = UUID.randomUUID()
        repeat(5) { i ->
            indexDoc(LogDocumentEs("idx-page-$i-${projectId}", projectId.toString(), "ing-1", "INFO", "msg $i", i.toLong() * 1000))
        }

        val page0Request = objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId, page = 0, size = 2))
        val page1Request = objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId, page = 1, size = 2))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(page0Request)
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))

        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(page1Request)
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.page").value(1))
    }

    @Test
    fun `should cap page size at 500`() {
        // Given
        val projectId = UUID.randomUUID()
        val request = objectMapper.writeValueAsString(LogSearchRequest(projectId = projectId, size = 9999))

        // When & Then
        val result = mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsString)
        body["size"].asInt() shouldBe 500
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
        // Given
        val projectId = UUID.randomUUID()
        indexDoc(LogDocumentEs("idx-fields-${projectId}", projectId.toString(), "ing-check", "WARN", "check fields", 5000L))

        // When & Then
        mockMvc.perform(
            post("/logs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(searchRequest(projectId))
                .cookie(Cookie("access_token", makeToken(userId)))
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").exists())
            .andExpect(jsonPath("$.items[0].ingestionId").value("ing-check"))
            .andExpect(jsonPath("$.items[0].level").value("WARN"))
            .andExpect(jsonPath("$.items[0].message").value("check fields"))
            .andExpect(jsonPath("$.items[0].timestamp").value(5000))
    }
}
