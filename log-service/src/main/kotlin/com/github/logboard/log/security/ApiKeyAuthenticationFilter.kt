package com.github.logboard.log.security

import com.github.logboard.log.service.LocalApiKeyCacheService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.LocalDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class ApiKeyAuthenticationFilter(
    private val localApiKeyCacheService: LocalApiKeyCacheService,
    @Value("\${api-key.hmac-secret}") private val hmacSecret: String
) : OncePerRequestFilter() {

    companion object {
        private const val API_KEY_HEADER = "X-Api-Key"
        private const val INGEST_PATH = "/logs/ingest"
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!request.requestURI.startsWith(INGEST_PATH)) {
            chain.doFilter(request, response)
            return
        }

        val rawKey = request.getHeader(API_KEY_HEADER)
        if (rawKey == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Api-Key header")
            return
        }

        val hash = computeHmac(rawKey, hmacSecret)
        val apiKey = localApiKeyCacheService.findByKeyHash(hash)

        if (apiKey == null || (apiKey.expiresAt != null && apiKey.expiresAt.isBefore(LocalDateTime.now()))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired API key")
            return
        }

        SecurityContextHolder.getContext().authentication = ApiKeyAuthentication(apiKey.projectId)
        chain.doFilter(request, response)
    }

    private fun computeHmac(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
