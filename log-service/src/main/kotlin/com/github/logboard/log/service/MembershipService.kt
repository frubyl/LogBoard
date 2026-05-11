package com.github.logboard.log.service

import com.github.logboard.log.client.CoreServiceClient
import com.github.logboard.log.model.MembershipResult
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class MembershipService(
    private val redisTemplate: StringRedisTemplate,
    private val coreServiceClient: CoreServiceClient
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MembershipService::class.java)
        private val CACHE_TTL = Duration.ofMinutes(5)
        private const val NOT_MEMBER = "NONE"
    }

    fun getMembership(userId: Long, projectId: UUID, token: String): String? {
        val cacheKey = "membership:$userId:$projectId"

        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            logger.debug("Cache hit for $cacheKey -> $cached")
            return if (cached == NOT_MEMBER) null else cached
        }

        logger.debug("Cache miss for $cacheKey, calling core service")
        return when (val result = coreServiceClient.getMembership(projectId, token)) {
            is MembershipResult.Found -> {
                redisTemplate.opsForValue().set(cacheKey, result.role, CACHE_TTL)
                result.role
            }
            is MembershipResult.NotMember -> {
                redisTemplate.opsForValue().set(cacheKey, NOT_MEMBER, CACHE_TTL)
                null
            }
            is MembershipResult.Unavailable -> {
                logger.warn("Core service unavailable for project $projectId, no cache entry — returning 403")
                null
            }
        }
    }
}
