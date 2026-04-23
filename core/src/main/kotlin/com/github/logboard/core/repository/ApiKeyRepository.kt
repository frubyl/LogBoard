package com.github.logboard.core.repository

import com.github.logboard.core.model.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findAllByProjectId(projectId: UUID): List<ApiKey>
    fun findByKeyHash(keyHash: String): ApiKey?
}
