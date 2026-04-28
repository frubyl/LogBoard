package com.github.logboard.log.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import java.util.UUID

@ContextConfiguration(classes = [LocalApiKeyCacheServiceTest.TestConfig::class])
class LocalApiKeyCacheServiceTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        val projectId: UUID = UUID.randomUUID()
        val keyId: UUID = UUID.randomUUID()
        val keyHash = "abc123hash"
        val apiKey = LocalApiKey(id = keyId, projectId = projectId, keyHash = keyHash)

        beforeEach {
            TestConfig.cacheManager.getCache("apiKeys")!!.clear()
            reset(TestConfig.repository)
        }

        describe("findByKeyHash") {
            it("calls repository on first request") {
                whenever(TestConfig.repository.findByKeyHash(keyHash)).thenReturn(apiKey)

                val result = TestConfig.service.findByKeyHash(keyHash)

                result shouldNotBe null
                result!!.id shouldBe keyId
                verify(TestConfig.repository, times(1)).findByKeyHash(keyHash)
            }

            it("returns cached value on second request without hitting repository") {
                whenever(TestConfig.repository.findByKeyHash(keyHash)).thenReturn(apiKey)
                TestConfig.service.findByKeyHash(keyHash)

                val result = TestConfig.service.findByKeyHash(keyHash)

                result!!.id shouldBe keyId
                verify(TestConfig.repository, times(1)).findByKeyHash(keyHash)
            }
        }

        describe("put") {
            it("populates cache so next findByKeyHash skips repository") {
                TestConfig.service.put(apiKey)

                val result = TestConfig.service.findByKeyHash(keyHash)

                result!!.id shouldBe keyId
                verify(TestConfig.repository, times(0)).findByKeyHash(keyHash)
            }
        }

        describe("evict") {
            it("removes entry so next findByKeyHash hits repository again") {
                whenever(TestConfig.repository.findByKeyHash(keyHash)).thenReturn(apiKey)
                TestConfig.service.findByKeyHash(keyHash)

                TestConfig.service.evict(keyHash)
                TestConfig.service.findByKeyHash(keyHash)

                verify(TestConfig.repository, times(2)).findByKeyHash(keyHash)
            }
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
}
