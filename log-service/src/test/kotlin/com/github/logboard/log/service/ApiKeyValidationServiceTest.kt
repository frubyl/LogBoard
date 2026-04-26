package com.github.logboard.log.service

import com.github.logboard.log.config.ApiKeyProperties
import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ApiKeyValidationServiceTest : DescribeSpec({

    val hmacSecret = "testHmacSecret"
    val apiKeyRepo = mock<LocalApiKeyRepository>()
    val apiKeyProperties = ApiKeyProperties(hmacSecret = hmacSecret)
    val service = ApiKeyValidationService(apiKeyRepo, apiKeyProperties)

    fun hmacSha256(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(key)
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    val projectId = UUID.randomUUID()
    val keyId = UUID.randomUUID()
    val rawKey = "lb_testkey123"

    describe("validate") {
        it("returns LocalApiKey for a valid non-expired key") {
            val localApiKey = LocalApiKey(id = keyId, projectId = projectId, keyHash = hmacSha256(rawKey), expiresAt = null)
            whenever(apiKeyRepo.findByKeyHash(hmacSha256(rawKey))).thenReturn(localApiKey)

            val result = service.validate(rawKey)

            result shouldNotBe null
            result!!.id shouldBe keyId
        }

        it("returns null when key hash not found") {
            whenever(apiKeyRepo.findByKeyHash(any())).thenReturn(null)

            service.validate("unknown_key") shouldBe null
        }

        it("returns null when key is expired") {
            val localApiKey = LocalApiKey(id = keyId, projectId = projectId, keyHash = hmacSha256(rawKey), expiresAt = LocalDateTime.now().minusHours(1))
            whenever(apiKeyRepo.findByKeyHash(hmacSha256(rawKey))).thenReturn(localApiKey)

            service.validate(rawKey) shouldBe null
        }

        it("returns LocalApiKey when key has future expiry") {
            val localApiKey = LocalApiKey(id = keyId, projectId = projectId, keyHash = hmacSha256(rawKey), expiresAt = LocalDateTime.now().plusDays(1))
            whenever(apiKeyRepo.findByKeyHash(hmacSha256(rawKey))).thenReturn(localApiKey)

            service.validate(rawKey) shouldNotBe null
        }
    }
})
