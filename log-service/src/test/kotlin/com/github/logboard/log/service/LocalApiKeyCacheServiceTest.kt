package com.github.logboard.log.service

import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import com.github.benmanes.caffeine.cache.Caffeine
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [LocalApiKeyCacheServiceTest.TestConfig::class])
class LocalApiKeyCacheServiceTest : DescribeSpec({

    val repository = TestConfig.repository
    val service = TestConfig.service

    beforeEach {
        TestConfig.cacheManager.getCache("apiKeys")!!.clear()
        org.mockito.kotlin.reset(repository)
    }

    val projectId: UUID = UUID.randomUUID()
    val keyId: UUID = UUID.randomUUID()
    val keyHash = "abc123hash"
    val apiKey = LocalApiKey(id = keyId, projectId = projectId, keyHash = keyHash)

    describe("findByKeyHash") {
        it("calls repository on first request") {
            whenever(repository.findByKeyHash(keyHash)).thenReturn(apiKey)

            val result = service.findByKeyHash(keyHash)

            result shouldNotBe null
            result!!.id shouldBe keyId
            verify(repository, times(1)).findByKeyHash(keyHash)
        }

        it("returns cached value on second request without hitting repository") {
            whenever(repository.findByKeyHash(keyHash)).thenReturn(apiKey)
            service.findByKeyHash(keyHash)

            val result = service.findByKeyHash(keyHash)

            result!!.id shouldBe keyId
            verify(repository, times(1)).findByKeyHash(keyHash)
        }
    }

    describe("put") {
        it("populates cache so next findByKeyHash skips repository") {
            service.put(apiKey)

            val result = service.findByKeyHash(keyHash)

            result!!.id shouldBe keyId
            verify(repository, times(0)).findByKeyHash(keyHash)
        }
    }

    describe("evict") {
        it("removes entry so next findByKeyHash hits repository again") {
            whenever(repository.findByKeyHash(keyHash)).thenReturn(apiKey)
            service.findByKeyHash(keyHash)

            service.evict(keyHash)
            service.findByKeyHash(keyHash)

            verify(repository, times(2)).findByKeyHash(keyHash)
        }
    }

    @Configuration
    @EnableCaching
    class TestConfig {
        companion object {
            val repository: LocalApiKeyRepository = mock()
            lateinit var service: LocalApiKeyCacheService
            lateinit var cacheManager: CaffeineCacheManager
        }

        @Bean
        fun localApiKeyRepository(): LocalApiKeyRepository = repository

        @Bean
        fun caffeineCacheManager(): CaffeineCacheManager {
            val manager = CaffeineCacheManager("apiKeys")
            manager.setCaffeine(Caffeine.newBuilder().maximumSize(1000))
            cacheManager = manager
            return manager
        }

        @Bean
        fun localApiKeyCacheService(repo: LocalApiKeyRepository): LocalApiKeyCacheService {
            service = LocalApiKeyCacheService(repo)
            return service
        }
    }
})
