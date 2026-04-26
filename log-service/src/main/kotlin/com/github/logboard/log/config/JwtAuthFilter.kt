package com.github.logboard.log.config

import com.github.logboard.log.util.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

data class JwtPrincipal(val userId: Long)

@Component
class JwtAuthFilter(private val jwtUtil: JwtUtil) : OncePerRequestFilter() {

    private companion object {
        private val log = LoggerFactory.getLogger(JwtAuthFilter::class.java)
        private const val ACCESS_TOKEN_COOKIE = "access_token"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.cookies?.find { it.name == ACCESS_TOKEN_COOKIE }?.value

        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            try {
                if (jwtUtil.isTokenValid(token)) {
                    val userId = jwtUtil.extractUserId(token)
                    val principal = JwtPrincipal(userId)
                    val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                    SecurityContextHolder.getContext().authentication = auth
                    log.debug("JWT authenticated user: $userId")
                }
            } catch (e: Exception) {
                log.error("JWT authentication failed: ${e.message}")
            }
        }

        filterChain.doFilter(request, response)
    }
}
