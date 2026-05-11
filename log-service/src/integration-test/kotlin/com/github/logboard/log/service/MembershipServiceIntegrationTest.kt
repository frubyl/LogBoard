package com.github.logboard.log.service

import com.github.logboard.log.client.CoreServiceClientImpl
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

class MembershipServiceIntegrationTest : DescribeSpec({

    val redisContainer = GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine"))
        .apply {
            withExposedPorts(6379)
            start()
        }

    val redisConfig = RedisStandaloneConfiguration(redisContainer.host, redisContainer.getMappedPort(6379))
    val connectionFactory = LettuceConnectionFactory(redisConfig).apply { afterPropertiesSet() }
    val redisTemplate = StringRedisTemplate(connectionFactory)

    val mockWebServer = MockWebServer()

    beforeSpec {
        mockWebServer.start()
    }

    afterSpec {
        mockWebServer.shutdown()
        connectionFactory.destroy()
        redisContainer.stop()
    }

    beforeEach {
        redisTemplate.keys("membership:*").forEach { redisTemplate.delete(it) }
    }

    fun buildService(): MembershipService {
        val url = "http://localhost:${mockWebServer.port}"
        val client = CoreServiceClientImpl(url)
        return MembershipService(redisTemplate, client)
    }

    val userId = 1L
    val projectId = UUID.randomUUID()
    val token = "valid-jwt-token"
    val cacheKey = "membership:$userId:$projectId"

    describe("getMembership") {

        it("получает роль от core service при промахе кеша") {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"role":"OWNER"}""")
            )

            val result = buildService().getMembership(userId, projectId, token)

            result shouldBe "OWNER"
        }

        it("кеширует результат в Redis после обращения к core service") {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"role":"ADMIN"}""")
            )

            buildService().getMembership(userId, projectId, token)

            val cached = redisTemplate.opsForValue().get(cacheKey)
            cached shouldBe "ADMIN"
        }

        it("использует кеш при повторном запросе без обращения к core service") {
            redisTemplate.opsForValue().set(cacheKey, "READER")
            val service = buildService()
            val requestsBefore = mockWebServer.requestCount

            val result = service.getMembership(userId, projectId, token)

            result shouldBe "READER"
            mockWebServer.requestCount shouldBe requestsBefore
        }

        it("кеширует NONE при ответе 403 от core service") {
            mockWebServer.enqueue(MockResponse().setResponseCode(403))

            val result = buildService().getMembership(userId, projectId, token)

            result shouldBe null
            val cached = redisTemplate.opsForValue().get(cacheKey)
            cached shouldBe "NONE"
        }

        it("возвращает null при кешированном значении NONE без обращения к core service") {
            redisTemplate.opsForValue().set(cacheKey, "NONE")
            val service = buildService()
            val requestsBefore = mockWebServer.requestCount

            val result = service.getMembership(userId, projectId, token)

            result shouldBe null
            mockWebServer.requestCount shouldBe requestsBefore
        }

        it("не кеширует результат при недоступности core service (5xx)") {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val result = buildService().getMembership(userId, projectId, token)

            result shouldBe null
            val cached = redisTemplate.opsForValue().get(cacheKey)
            cached shouldBe null
        }

        it("кеш имеет TTL") {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"role":"READER"}""")
            )

            buildService().getMembership(userId, projectId, token)

            val ttl = redisTemplate.getExpire(cacheKey)
            (ttl != null && ttl > 0L) shouldBe true
        }

        it("изолирует кеш по парам userId+projectId") {
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

            redisTemplate.opsForValue().get("membership:$userId:$projectId") shouldBe "OWNER"
            redisTemplate.opsForValue().get("membership:$userId:$projectId2") shouldBe "OWNER"
        }
    }
})
