package com.github.logboard.log.config

import com.github.logboard.log.service.ApiKeyValidationService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

data class ApiKeyPrincipal(val keyId: UUID, val projectId: UUID)

@Component
class ApiKeyAuthFilter(
    private val apiKeyValidationService: ApiKeyValidationService
) : OncePerRequestFilter() {

    private companion object {
        private val log = LoggerFactory.getLogger(ApiKeyAuthFilter::class.java)
        private const val API_KEY_HEADER = "X-API-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rawKey = request.getHeader(API_KEY_HEADER)

        if (rawKey != null && SecurityContextHolder.getContext().authentication == null) {
            try {
                val apiKey = apiKeyValidationService.validate(rawKey)
                if (apiKey != null) {
                    val principal = ApiKeyPrincipal(keyId = apiKey.id, projectId = apiKey.projectId)
                    val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                    SecurityContextHolder.getContext().authentication = auth
                    log.debug("API key authenticated for project: ${apiKey.projectId}")
                } else {
                    log.debug("Invalid or expired API key presented")
                }
            } catch (e: Exception) {
                log.error("API key authentication failed: ${e.message}")
            }
        }

        filterChain.doFilter(request, response)
    }
}
