package com.github.logboard.core.controller

import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.AuthResponse
import com.github.logboard.core.dto.RefreshTokenRequest
import com.github.logboard.core.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val authenticationManager: AuthenticationManager
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody authRequest: AuthRequest): ResponseEntity<Void> {
        userService.registerUser(authRequest)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody authRequest: AuthRequest): ResponseEntity<AuthResponse> {
        // Authenticate user
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                authRequest.username,
                authRequest.password
            )
        )
        
        // Generate tokens
        val authResponse = userService.generateTokensForUser(authRequest.username)
        return ResponseEntity.ok(authResponse)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody refreshTokenRequest: RefreshTokenRequest): ResponseEntity<AuthResponse> {
        val authResponse = userService.refreshToken(refreshTokenRequest.refreshToken)
        return ResponseEntity.ok(authResponse)
    }
}
