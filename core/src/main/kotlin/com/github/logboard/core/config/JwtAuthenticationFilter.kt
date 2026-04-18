package com.github.logboard.core.config

import com.github.logboard.core.service.UserService
import com.github.logboard.core.util.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val userService: UserService
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
        private const val ACCESS_TOKEN_COOKIE = "access_token"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val token = getAccessTokenFromCookie(request)

            if (token == null) {
                filterChain.doFilter(request, response)
                return
            }

            val userId = jwtUtil.extractUserId(token)
            val user = userService.loadUserById(userId)

            if (jwtUtil.validateToken(token, user) && SecurityContextHolder.getContext().authentication == null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    emptyList()
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication

                log.debug("User authenticated: ${user.username}")
            }
        } catch (e: Exception) {
            log.error("JWT authentication failed: ${e.message}")
        }

        filterChain.doFilter(request, response)
    }

    private fun getAccessTokenFromCookie(request: HttpServletRequest): String? {
        return request.cookies?.find { it.name == ACCESS_TOKEN_COOKIE }?.value
    }
}
