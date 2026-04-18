package com.github.logboard.core.controller

import com.github.logboard.core.config.JwtProperties
import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.AuthResponse
import com.github.logboard.core.service.UserService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
class AuthController(
    private val userService: UserService,
    private val authenticationManager: AuthenticationManager,
    private val jwtProperties: JwtProperties
) {

    companion object {
        private const val ACCESS_TOKEN_COOKIE = "access_token"
        private const val REFRESH_TOKEN_COOKIE = "refresh_token"
    }

    @PostMapping("/register")
    fun register(@Valid @RequestBody authRequest: AuthRequest): ResponseEntity<Void> {
        userService.registerUser(authRequest)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody authRequest: AuthRequest,
        response: HttpServletResponse
    ): ResponseEntity<Unit> {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                authRequest.username,
                authRequest.password
            )
        )

        val authResponse = userService.generateTokensForUser(authRequest.username)
        setAuthCookies(response, authResponse)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/refresh")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Unit> {
        val refreshToken = getCookieValue(request, REFRESH_TOKEN_COOKIE)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val authResponse = userService.refreshToken(refreshToken)
        setAuthCookies(response, authResponse)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Unit> {
        clearAuthCookies(response)
        return ResponseEntity.ok().build()
    }

    private fun setAuthCookies(response: HttpServletResponse, authResponse: AuthResponse) {
        response.addCookie(createCookie(ACCESS_TOKEN_COOKIE, authResponse.accessToken, jwtProperties.accessTokenCookieMaxAge))
        response.addCookie(createCookie(REFRESH_TOKEN_COOKIE, authResponse.refreshToken, jwtProperties.refreshTokenCookieMaxAge))
    }

    private fun createCookie(name: String, value: String, maxAge: Int): Cookie {
        return Cookie(name, value).apply {
            setHttpOnly(true)
            secure = false
            path = "/"
            this.maxAge = maxAge
        }
    }

    private fun clearAuthCookies(response: HttpServletResponse) {
        response.addCookie(createCookie(ACCESS_TOKEN_COOKIE, "", 0))
        response.addCookie(createCookie(REFRESH_TOKEN_COOKIE, "", 0))
    }

    private fun getCookieValue(request: HttpServletRequest, name: String): String? {
        return request.cookies?.find { it.name == name }?.value
    }
}
