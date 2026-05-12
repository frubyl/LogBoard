package com.github.logboard.log.service

import com.github.logboard.log.client.CoreServiceClient
import com.github.logboard.log.model.MembershipResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.UUID

class MembershipServiceTest : DescribeSpec({

    val redisTemplate = mock<StringRedisTemplate>()
    val ops = mock<ValueOperations<String, String>>()
    val coreServiceClient = mock<CoreServiceClient>()
    val service = MembershipService(redisTemplate, coreServiceClient)

    val projectId = UUID.randomUUID()
    val userId = 42L
    val token = "jwt-token"
    val cacheKey = "membership:$userId:$projectId"

    beforeEach {
        reset(redisTemplate, ops, coreServiceClient)
        whenever(redisTemplate.opsForValue()).thenReturn(ops)
    }

    describe("getMembership") {

        it("возвращает роль из Redis при попадании в кеш") {
            whenever(ops.get(cacheKey)).thenReturn("OWNER")

            val result = service.getMembership(userId, projectId, token)

            result shouldBe "OWNER"
            verify(coreServiceClient, never()).getMembership(any(), any())
        }

        it("возвращает null при кешированном значении NONE") {
            whenever(ops.get(cacheKey)).thenReturn("NONE")

            val result = service.getMembership(userId, projectId, token)

            result shouldBe null
            verify(coreServiceClient, never()).getMembership(any(), any())
        }

        it("вызывает core service при промахе кеша") {
            whenever(ops.get(cacheKey)).thenReturn(null)
            whenever(coreServiceClient.getMembership(projectId, token)).thenReturn(MembershipResult.Found("ADMIN"))

            val result = service.getMembership(userId, projectId, token)

            result shouldBe "ADMIN"
            verify(coreServiceClient).getMembership(projectId, token)
        }

        it("кеширует роль после успешного ответа от core service") {
            whenever(ops.get(cacheKey)).thenReturn(null)
            whenever(coreServiceClient.getMembership(projectId, token)).thenReturn(MembershipResult.Found("READER"))

            service.getMembership(userId, projectId, token)

            verify(ops).set(eq(cacheKey), eq("READER"), any<Duration>())
        }

        it("кеширует NONE при NotMember от core service") {
            whenever(ops.get(cacheKey)).thenReturn(null)
            whenever(coreServiceClient.getMembership(projectId, token)).thenReturn(MembershipResult.NotMember)

            val result = service.getMembership(userId, projectId, token)

            result shouldBe null
            verify(ops).set(eq(cacheKey), eq("NONE"), any<Duration>())
        }

        it("не кеширует при Unavailable и возвращает null") {
            whenever(ops.get(cacheKey)).thenReturn(null)
            whenever(coreServiceClient.getMembership(projectId, token)).thenReturn(MembershipResult.Unavailable)

            val result = service.getMembership(userId, projectId, token)

            result shouldBe null
            verify(ops, never()).set(any(), any(), any<Duration>())
        }

        it("ключ кеша уникален для пары userId+projectId") {
            val anotherProjectId = UUID.randomUUID()
            whenever(ops.get(any())).thenReturn(null)
            whenever(coreServiceClient.getMembership(any(), any())).thenReturn(MembershipResult.Found("READER"))

            service.getMembership(userId, projectId, token)
            service.getMembership(userId, anotherProjectId, token)

            verify(ops).get("membership:$userId:$projectId")
            verify(ops).get("membership:$userId:$anotherProjectId")
        }

        it("TTL кеша не нулевой при успешном ответе") {
            whenever(ops.get(cacheKey)).thenReturn(null)
            whenever(coreServiceClient.getMembership(any(), any())).thenReturn(MembershipResult.Found("OWNER"))

            service.getMembership(userId, projectId, token)

            verify(ops).set(eq(cacheKey), any<String>(), any<Duration>())
        }
    }
})
