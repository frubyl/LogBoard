package com.github.logboard.core.controller

import com.github.logboard.core.config.JwtProperties
import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.AuthResponse
import com.github.logboard.core.model.User
import com.github.logboard.core.service.UserService
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.lang.reflect.Field

@ExtendWith(MockitoExtension::class)
class AuthControllerTest {

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var authenticationManager: AuthenticationManager

    @Mock
    private lateinit var jwtProperties: JwtProperties

    @InjectMocks
    private lateinit var authController: AuthController

    private fun setupJwtProperties() {
        `when`(jwtProperties.accessTokenCookieMaxAge).thenReturn(900)
        `when`(jwtProperties.refreshTokenCookieMaxAge).thenReturn(604800)
    }

    private fun setCookies(request: MockHttpServletRequest, cookies: Array<Cookie>) {
        val field: Field = MockHttpServletRequest::class.java.getDeclaredField("cookies")
        field.isAccessible = true
        field.set(request, cookies)
    }

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
    fun `login should set cookies when credentials are valid`() {
        // Given
        setupJwtProperties()
        val authRequest = AuthRequest("testuser", "password123")
        val authResponse = AuthResponse("access-token", "refresh-token")
        val response = MockHttpServletResponse()
        `when`(userService.generateTokensForUser(authRequest.username)).thenReturn(authResponse)

        // When
        val result = authController.login(authRequest, response)

        // Then
        result.statusCode shouldBe HttpStatus.OK
        result.body shouldBe null

        val cookies = response.cookies
        cookies.size shouldBe 2
        cookies.find { it.name == "access_token" }?.value shouldBe "access-token"
        cookies.find { it.name == "refresh_token" }?.value shouldBe "refresh-token"

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken::class.java))
        verify(userService).generateTokensForUser(authRequest.username)
    }

    @Test
    fun `refresh should set new cookies when refresh token is valid`() {
        // Given
        setupJwtProperties()
        val request = MockHttpServletRequest()
        setCookies(request, arrayOf(Cookie("refresh_token", "valid-refresh-token")))
        val response = MockHttpServletResponse()
        val authResponse = AuthResponse("new-access-token", "new-refresh-token")
        `when`(userService.refreshToken("valid-refresh-token")).thenReturn(authResponse)

        // When
        val result = authController.refresh(request, response)

        // Then
        result.statusCode shouldBe HttpStatus.OK
        result.body shouldBe null

        val cookies = response.cookies
        cookies.size shouldBe 2
        cookies.find { it.name == "access_token" }?.value shouldBe "new-access-token"
        cookies.find { it.name == "refresh_token" }?.value shouldBe "new-refresh-token"

        verify(userService).refreshToken("valid-refresh-token")
    }

    @Test
    fun `refresh should return 401 when refresh token is missing`() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        // When
        val result = authController.refresh(request, response)

        // Then
        result.statusCode shouldBe HttpStatus.UNAUTHORIZED

        verifyNoInteractions(userService)
    }

    @Test
    fun `logout should clear cookies`() {
        // Given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        // When
        val result = authController.logout(request, response)

        // Then
        result.statusCode shouldBe HttpStatus.OK
        result.body shouldBe null

        val cookies = response.cookies
        cookies.size shouldBe 2
        cookies.find { it.name == "access_token" }?.value shouldBe ""
        cookies.find { it.name == "refresh_token" }?.value shouldBe ""
        cookies.forEach { it.maxAge shouldBe 0 }
    }
}
