package com.github.logboard.core.service

import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.AuthResponse
import com.github.logboard.core.exception.common.AlreadyExistsException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.exception.authentication.UnauthorizedException
import com.github.logboard.core.model.User
import com.github.logboard.core.repository.UserRepository
import com.github.logboard.core.util.JwtUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var jwtUtil: JwtUtil

    @InjectMocks
    private lateinit var userService: UserService

    private lateinit var authRequest: AuthRequest
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        authRequest = AuthRequest("testuser", "password123")
        user = User(
            id = 1L,
            username = "testuser",
            password = "encodedPassword"
        )
    }

    @Test
    fun `registerUser should save user when username is available`() {
        // Given
        `when`(userRepository.existsByUsername(authRequest.username)).thenReturn(false)
        `when`(passwordEncoder.encode(authRequest.password)).thenReturn("encodedPassword")
        `when`(userRepository.save(any(User::class.java))).thenReturn(user)

        // When
        val result = userService.registerUser(authRequest)

        // Then
        result.shouldNotBeNull()
        result.username shouldBe authRequest.username
        verify(userRepository).existsByUsername(authRequest.username)
        verify(passwordEncoder).encode(authRequest.password)
        verify(userRepository).save(any(User::class.java))
    }

    @Test
    fun `registerUser should throw AlreadyExistsException when username already exists`() {
        // Given
        `when`(userRepository.existsByUsername(authRequest.username)).thenReturn(true)

        // When & Then
        val exception = shouldThrow<AlreadyExistsException> {
            userService.registerUser(authRequest)
        }

        exception.message shouldBe "User with username ${authRequest.username} already exists"
        verify(userRepository).existsByUsername(authRequest.username)
        verify(userRepository, never()).save(any(User::class.java))
    }

    @Test
    fun `loadUserByUsername should return user when user exists`() {
        // Given
        `when`(userRepository.findByUsername(authRequest.username)).thenReturn(user)

        // When
        val result = userService.loadUserByUsername(authRequest.username)

        // Then
        result.shouldNotBeNull()
        result.username shouldBe authRequest.username
        verify(userRepository).findByUsername(authRequest.username)
    }

    @Test
    fun `loadUserByUsername should throw NotFoundException when user does not exist`() {
        // Given
        `when`(userRepository.findByUsername(authRequest.username)).thenReturn(null)

        // When & Then
        shouldThrow<NotFoundException> {
            userService.loadUserByUsername(authRequest.username)
        }

        verify(userRepository).findByUsername(authRequest.username)
    }

    @Test
    fun `generateTokensForUser should return auth response with valid tokens`() {
        // Given
        val accessToken = "access-token"
        val refreshToken = "refresh-token"

        `when`(userRepository.findByUsername(authRequest.username)).thenReturn(user)
        `when`(jwtUtil.generateAccessToken(user)).thenReturn(accessToken)
        `when`(jwtUtil.generateRefreshToken(user)).thenReturn(refreshToken)

        // When
        val result = userService.generateTokensForUser(authRequest.username)

        // Then
        result.shouldNotBeNull()
        result.accessToken shouldBe accessToken
        result.refreshToken shouldBe refreshToken
        verify(jwtUtil).generateAccessToken(user)
        verify(jwtUtil).generateRefreshToken(user)
    }

    @Test
    fun `loadUserById should return user when user exists`() {
        // Given
        val userId = 1L
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))

        // When
        val result = userService.loadUserById(userId)

        // Then
        result.shouldNotBeNull()
        result.id shouldBe userId
        result.username shouldBe user.username
        verify(userRepository).findById(userId)
    }

    @Test
    fun `loadUserById should throw NotFoundException when user does not exist`() {
        // Given
        val userId = 999L
        `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

        // When & Then
        val exception = shouldThrow<NotFoundException> {
            userService.loadUserById(userId)
        }

        exception.message shouldBe "User not found with id: $userId"
        verify(userRepository).findById(userId)
    }

    @Test
    fun `refreshToken should return new tokens when refresh token is valid`() {
        // Given
        val oldRefreshToken = "old-refresh-token"
        val newAccessToken = "new-access-token"
        val newRefreshToken = "new-refresh-token"
        val userId = 1L

        `when`(jwtUtil.extractUserId(oldRefreshToken)).thenReturn(userId)
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))
        `when`(jwtUtil.validateToken(oldRefreshToken, user)).thenReturn(true)
        `when`(jwtUtil.generateAccessToken(user)).thenReturn(newAccessToken)
        `when`(jwtUtil.generateRefreshToken(user)).thenReturn(newRefreshToken)

        // When
        val result = userService.refreshToken(oldRefreshToken)

        // Then
        result.shouldNotBeNull()
        result.accessToken shouldBe newAccessToken
        result.refreshToken shouldBe newRefreshToken
        verify(jwtUtil).extractUserId(oldRefreshToken)
        verify(jwtUtil).validateToken(oldRefreshToken, user)
        verify(jwtUtil).generateAccessToken(user)
        verify(jwtUtil).generateRefreshToken(user)
    }

    @Test
    fun `refreshToken should throw UnauthorizedException when token is invalid`() {
        // Given
        val invalidRefreshToken = "invalid-refresh-token"
        val userId = 1L

        `when`(jwtUtil.extractUserId(invalidRefreshToken)).thenReturn(userId)
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))
        `when`(jwtUtil.validateToken(invalidRefreshToken, user)).thenReturn(false)

        // When & Then
        val exception = shouldThrow<UnauthorizedException> {
            userService.refreshToken(invalidRefreshToken)
        }

        exception.message shouldBe "Invalid refresh token"
        verify(jwtUtil).extractUserId(invalidRefreshToken)
        verify(jwtUtil).validateToken(invalidRefreshToken, user)
    }

    @Test
    fun `refreshToken should throw exception when user not found`() {
        // Given
        val refreshToken = "refresh-token"
        val userId = 999L

        `when`(jwtUtil.extractUserId(refreshToken)).thenReturn(userId)
        `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

        // When & Then
        shouldThrow<NotFoundException> {
            userService.refreshToken(refreshToken)
        }

        verify(jwtUtil).extractUserId(refreshToken)
        verify(userRepository).findById(userId)
    }
}
