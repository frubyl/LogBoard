package com.github.logboard.log

import com.github.logboard.log.config.ApiKeyProperties
import com.github.logboard.log.dto.LogIngestItem
import com.github.logboard.log.dto.LogIngestRequest
import com.github.logboard.log.dto.LogIngestResponse
import com.github.logboard.log.model.LogLevel
import com.github.logboard.log.repository.IngestionStatusRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LogIngestionIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var ingestionStatusRepository: IngestionStatusRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var apiKeyProperties: ApiKeyProperties

    @Test
    fun `ingest logs returns 200 and creates ingestion status`() {
        val projectId = UUID.randomUUID()
        val rawKey = setupApiKey(projectId)

        val request = LogIngestRequest(
            projectId = projectId,
            logs = listOf(
                LogIngestItem(level = LogLevel.INFO, message = "integration test log", timestamp = null),
                LogIngestItem(level = LogLevel.ERROR, message = "integration test error", timestamp = null)
            )
        )

        val headers = HttpHeaders().apply {
            set("X-API-Key", rawKey)
            set("Content-Type", "application/json")
        }

        val response = restTemplate.postForEntity(
            "/logs/ingest",
            HttpEntity(request, headers),
            LogIngestResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.ingestionId)

        val status = ingestionStatusRepository.findById(response.body!!.ingestionId)
        assert(status.isPresent)
        assertEquals(projectId, status.get().projectId)
        assertEquals(2, status.get().accepted)
    }

    @Test
    fun `ingest without API key returns 401`() {
        val request = LogIngestRequest(
            projectId = UUID.randomUUID(),
            logs = listOf(LogIngestItem(level = LogLevel.INFO, message = "test", timestamp = null))
        )

        val response = restTemplate.postForEntity(
            "/logs/ingest",
            HttpEntity(request, HttpHeaders()),
            String::class.java
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun setupApiKey(projectId: UUID): String {
        val rawKey = "lb_${UUID.randomUUID().toString().replace("-", "")}"
        val keyHash = hmacSha256(rawKey)
        val userId = insertUser()
        insertProject(projectId)
        insertProjectMember(projectId, userId)
        insertApiKey(projectId, keyHash, userId)
        return rawKey
    }

    private fun insertUser(): Long {
        jdbcTemplate.update(
            "INSERT INTO users (username, password, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
            "testuser-${UUID.randomUUID()}", "\$2a\$10\$test"
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM users", Long::class.java)!!
    }

    private fun insertProject(projectId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO projects (id, name, description, created_at, updated_at) VALUES (?::uuid, ?, ?, NOW(), NOW())",
            projectId.toString(), "Test Project", "Test"
        )
    }

    private fun insertProjectMember(projectId: UUID, userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO project_members (project_id, user_id, role, created_at, updated_at) VALUES (?::uuid, ?, ?, NOW(), NOW())",
            projectId.toString(), userId, "OWNER"
        )
    }

    private fun insertApiKey(projectId: UUID, keyHash: String, userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO api_keys (id, project_id, name, key_hash, created_by, created_at, updated_at) VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, NOW(), NOW())",
            projectId.toString(), "test-key", keyHash, userId
        )
    }

    private fun hmacSha256(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(apiKeyProperties.hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
