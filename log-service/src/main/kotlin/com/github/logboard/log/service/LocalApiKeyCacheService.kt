package com.github.logboard.log.service

import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class LocalApiKeyCacheService(private val repository: LocalApiKeyRepository) {

    @Cacheable("apiKeys", key = "#keyHash")
    fun findByKeyHash(keyHash: String): LocalApiKey? = repository.findByKeyHash(keyHash)

    @CachePut("apiKeys", key = "#entity.keyHash")
    fun put(entity: LocalApiKey): LocalApiKey = entity

    @CacheEvict("apiKeys", key = "#keyHash")
    fun evict(keyHash: String) = Unit
}
