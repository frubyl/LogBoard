package com.github.logboard.log.service

import com.github.logboard.log.config.ApiKeyProperties
import com.github.logboard.log.model.LocalApiKey
import com.github.logboard.log.repository.LocalApiKeyRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class ApiKeyValidationService(
    private val localApiKeyRepository: LocalApiKeyRepository,
    private val apiKeyProperties: ApiKeyProperties
) {

    private companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    fun validate(rawKey: String): LocalApiKey? {
        val keyHash = hmacSha256(rawKey)
        val apiKey = localApiKeyRepository.findByKeyHash(keyHash) ?: return null

        if (apiKey.expiresAt != null && apiKey.expiresAt.isBefore(LocalDateTime.now())) {
            return null
        }

        return apiKey
    }

    private fun hmacSha256(value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(apiKeyProperties.hmacSecret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKey)
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
