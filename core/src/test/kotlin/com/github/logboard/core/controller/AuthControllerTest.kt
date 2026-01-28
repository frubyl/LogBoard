package com.github.logboard.core.controller

import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.AuthResponse
import com.github.logboard.core.dto.RefreshTokenRequest
import com.github.logboard.core.model.User
import com.github.logboard.core.service.UserService
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager

@ExtendWith(MockitoExtension::class)
class AuthControllerTest {

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var authenticationManager: AuthenticationManager

    @InjectMocks
    private lateinit var authController: AuthController

    @Test
    fun `register should return success response when user is registered successfully`() {
        // Given
        val authRequest = AuthRequest("testuser", "password123")
        val user = User(id = 1L, username = "testuser", password = "encodedPassword")
        `when`(userService.registerUser(authRequest)).thenReturn(user)

        // When
        val response = authController.register(authRequest)

        // Then
        response.statusCode shouldBe HttpStatus.CREATED
        response.body shouldBe null

        verify(userService).registerUser(authRequest)
    }

    @Test
    fun `login should return auth response when credentials are valid`() {
        // Given
        val authRequest = AuthRequest("testuser", "password123")
        val authResponse = AuthResponse("access-token", "refresh-token")
        `when`(userService.generateTokensForUser(authRequest.username)).thenReturn(authResponse)

        // When
        val response = authController.login(authRequest)

        // Then
        response.statusCode shouldBe HttpStatus.OK
        val body = response.body as AuthResponse
        body.accessToken shouldBe "access-token"
        body.refreshToken shouldBe "refresh-token"

        verify(authenticationManager).authenticate(any())
        verify(userService).generateTokensForUser(authRequest.username)
    }

    @Test
    fun `refresh should return new auth response when refresh token is valid`() {
        // Given
        val refreshTokenRequest = RefreshTokenRequest("valid-refresh-token")
        val authResponse = AuthResponse("new-access-token", "new-refresh-token")
        `when`(userService.refreshToken("valid-refresh-token")).thenReturn(authResponse)

        // When
        val response = authController.refresh(refreshTokenRequest)

        // Then
        response.statusCode shouldBe HttpStatus.OK
        val body = response.body as AuthResponse
        body.accessToken shouldBe "new-access-token"
        body.refreshToken shouldBe "new-refresh-token"

        verify(userService).refreshToken("valid-refresh-token")
    }
}
