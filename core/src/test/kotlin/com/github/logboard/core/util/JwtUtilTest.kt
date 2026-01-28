package com.github.logboard.core.util

import com.github.logboard.core.config.JwtProperties
import com.github.logboard.core.model.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Date

@ExtendWith(MockitoExtension::class)
class JwtUtilTest {

    private lateinit var jwtUtil: JwtUtil
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        val jwtProperties = JwtProperties(
            secret = "mySecretKeyForLogBoardApplicationWhichIsVeryLongAndSecureForTesting",
            accessTokenExpiration = 3600000,  // 1 hour
            refreshTokenExpiration = 86400000  // 24 hours
        )
        jwtUtil = JwtUtil(jwtProperties)
        testUser = User(id = 1L, username = "testuser", password = "encodedPassword")
    }

    @Test
    fun `generateAccessToken should create valid token`() {
        // When
        val token = jwtUtil.generateAccessToken(testUser)

        // Then
        token shouldNotBe null
        token.split(".").size shouldBe 3  // JWT has 3 parts
    }

    @Test
    fun `generateRefreshToken should create valid token`() {
        // When
        val token = jwtUtil.generateRefreshToken(testUser)

        // Then
        token shouldNotBe null
        token.split(".").size shouldBe 3  // JWT has 3 parts
    }

    @Test
    fun `generateAccessToken should throw exception when user id is null`() {
        // Given
        val userWithoutId = User(id = null, username = "testuser", password = "password")

        // When & Then
        shouldThrow<IllegalArgumentException> {
            jwtUtil.generateAccessToken(userWithoutId)
        }
    }

    @Test
    fun `extractUserId should return correct user id from token`() {
        // Given
        val token = jwtUtil.generateAccessToken(testUser)

        // When
        val userId = jwtUtil.extractUserId(token)

        // Then
        userId shouldBe 1L
    }

    @Test
    fun `extractUserId should throw exception for invalid token`() {
        // Given
        val invalidToken = "invalid.token.here"

        // When & Then
        shouldThrow<IllegalArgumentException> {
            jwtUtil.extractUserId(invalidToken)
        }
    }

    @Test
    fun `validateToken should return true for valid token`() {
        // Given
        val token = jwtUtil.generateAccessToken(testUser)

        // When
        val isValid = jwtUtil.validateToken(token, testUser)

        // Then
        isValid shouldBe true
    }

    @Test
    fun `validateToken should return false for token with wrong user id`() {
        // Given
        val token = jwtUtil.generateAccessToken(testUser)
        val differentUser = User(id = 2L, username = "different", password = "password")

        // When
        val isValid = jwtUtil.validateToken(token, differentUser)

        // Then
        isValid shouldBe false
    }

    @Test
    fun `validateToken should return false for invalid token`() {
        // Given
        val invalidToken = "invalid.token.here"

        // When
        val isValid = jwtUtil.validateToken(invalidToken, testUser)

        // Then
        isValid shouldBe false
    }

    @Test
    fun `validateToken should return false for expired token`() {
        // Given
        val expiredJwtProperties = JwtProperties(
            secret = "mySecretKeyForLogBoardApplicationWhichIsVeryLongAndSecureForTesting",
            accessTokenExpiration = 1,  // 1 millisecond
            refreshTokenExpiration = 86400000
        )
        val expiredJwtUtil = JwtUtil(expiredJwtProperties)
        val token = expiredJwtUtil.generateAccessToken(testUser)

        // Wait for token to expire
        Thread.sleep(10)

        // When
        val isValid = expiredJwtUtil.validateToken(token, testUser)

        // Then
        isValid shouldBe false
    }

    @Test
    fun `access token and refresh token should be different`() {
        // When
        val accessToken = jwtUtil.generateAccessToken(testUser)
        val refreshToken = jwtUtil.generateRefreshToken(testUser)

        // Then
        accessToken shouldNotBe refreshToken
    }

    @Test
    fun `tokens should contain user id claim`() {
        // Given
        val token = jwtUtil.generateAccessToken(testUser)

        // When
        val userId = jwtUtil.extractUserId(token)

        // Then
        userId shouldBe testUser.id
    }
}
