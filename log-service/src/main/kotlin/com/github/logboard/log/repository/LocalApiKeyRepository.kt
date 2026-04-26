package com.github.logboard.log.repository

import com.github.logboard.log.model.LocalApiKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LocalApiKeyRepository : JpaRepository<LocalApiKey, UUID> {
    fun findByKeyHash(keyHash: String): LocalApiKey?
}
