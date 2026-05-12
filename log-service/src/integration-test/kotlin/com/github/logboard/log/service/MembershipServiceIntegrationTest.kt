package com.github.logboard.log.service

import com.github.logboard.log.client.CoreServiceClientImpl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MembershipServiceIntegrationTest {

    private val redisContainer = GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine"))
        .apply { withExposedPorts(6379) }

    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private val mockWebServer = MockWebServer()

    private val userId = 1L
    private val projectId = UUID.randomUUID()
    private val token = "valid-jwt-token"
    private val cacheKey = "membership:$userId:$projectId"

    @BeforeAll
    fun start() {
        redisContainer.start()
        mockWebServer.start()
        val redisConfig = RedisStandaloneConfiguration(redisContainer.host, redisContainer.getMappedPort(6379))
        connectionFactory = LettuceConnectionFactory(redisConfig).apply { afterPropertiesSet() }
        redisTemplate = StringRedisTemplate(connectionFactory)
    }

    @BeforeEach
    fun clearCache() {
        redisTemplate.keys("membership:*").forEach { redisTemplate.delete(it) }
    }

    @AfterAll
    fun stop() {
        mockWebServer.shutdown()
        connectionFactory.destroy()
        redisContainer.stop()
    }

    private fun buildService(): MembershipService {
        val url = "http://localhost:${mockWebServer.port}"
        val client = CoreServiceClientImpl(url)
        return MembershipService(redisTemplate, client)
    }

    @Test
    fun `should fetch role from core service on cache miss`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"role":"OWNER"}""")
        )

        val result = buildService().getMembership(userId, projectId, token)

        assertEquals("OWNER", result)
    }

    @Test
    fun `should cache result in Redis after fetching from core service`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"role":"ADMIN"}""")
        )

        buildService().getMembership(userId, projectId, token)

        assertEquals("ADMIN", redisTemplate.opsForValue().get(cacheKey))
    }

    @Test
    fun `should use cache on subsequent request without calling core service`() {
        redisTemplate.opsForValue().set(cacheKey, "READER")
        val service = buildService()
        val requestsBefore = mockWebServer.requestCount

        val result = service.getMembership(userId, projectId, token)

        assertEquals("READER", result)
        assertEquals(requestsBefore, mockWebServer.requestCount)
    }

    @Test
    fun `should cache NONE when core service returns 403`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val result = buildService().getMembership(userId, projectId, token)

        assertNull(result)
        assertEquals("NONE", redisTemplate.opsForValue().get(cacheKey))
    }

    @Test
    fun `should return null for cached NONE without calling core service`() {
        redisTemplate.opsForValue().set(cacheKey, "NONE")
        val service = buildService()
        val requestsBefore = mockWebServer.requestCount

        val result = service.getMembership(userId, projectId, token)

        assertNull(result)
        assertEquals(requestsBefore, mockWebServer.requestCount)
    }

    @Test
    fun `should not cache result when core service is unavailable (5xx)`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = buildService().getMembership(userId, projectId, token)

        assertNull(result)
        assertNull(redisTemplate.opsForValue().get(cacheKey))
    }

    @Test
    fun `should set TTL on cache entry`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"role":"READER"}""")
        )

        buildService().getMembership(userId, projectId, token)

        val ttl = redisTemplate.getExpire(cacheKey)
        assertNotNull(ttl)
        assertTrue(ttl!! > 0L)
    }

    @Test
    fun `should isolate cache by userId and projectId pair`() {
        val projectId2 = UUID.randomUUID()
        repeat(2) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"role":"OWNER"}""")
            )
        }
        val service = buildService()

        service.getMembership(userId, projectId, token)
        service.getMembership(userId, projectId2, token)

        assertEquals("OWNER", redisTemplate.opsForValue().get("membership:$userId:$projectId"))
        assertEquals("OWNER", redisTemplate.opsForValue().get("membership:$userId:$projectId2"))
    }
}
