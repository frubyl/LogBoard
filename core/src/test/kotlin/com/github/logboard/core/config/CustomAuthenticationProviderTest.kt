package com.github.logboard.core.config

import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.User
import com.github.logboard.core.service.UserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
class CustomAuthenticationProviderTest {

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var authenticationProvider: CustomAuthenticationProvider

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser = User(
            id = 1L,
            username = "testuser",
            password = "encodedPassword"
        )
    }

    @Test
    fun `authenticate should return authentication when credentials are correct`() {
        // Given
        val username = "testuser"
        val password = "password123"
        val authentication = UsernamePasswordAuthenticationToken(username, password)

        `when`(userService.loadUserByUsername(username)).thenReturn(testUser)
        `when`(passwordEncoder.matches(password, testUser.password)).thenReturn(true)

        // When
        val result = authenticationProvider.authenticate(authentication)

        // Then
        result shouldNotBe null
        result.principal shouldBe testUser
        result.credentials shouldBe password
        result.authorities.size shouldBe 1
        result.authorities.first().authority shouldBe "USER"

        verify(userService).loadUserByUsername(username)
        verify(passwordEncoder).matches(password, testUser.password)
    }

    @Test
    fun `authenticate should throw BadCredentialsException when password is incorrect`() {
        // Given
        val username = "testuser"
        val password = "wrongpassword"
        val authentication = UsernamePasswordAuthenticationToken(username, password)

        `when`(userService.loadUserByUsername(username)).thenReturn(testUser)
        `when`(passwordEncoder.matches(password, testUser.password)).thenReturn(false)

        // When & Then
        shouldThrow<BadCredentialsException> {
            authenticationProvider.authenticate(authentication)
        }

        verify(userService).loadUserByUsername(username)
        verify(passwordEncoder).matches(password, testUser.password)
    }

    @Test
    fun `authenticate should throw BadCredentialsException when user not found`() {
        // Given
        val username = "nonexistent"
        val password = "password123"
        val authentication = UsernamePasswordAuthenticationToken(username, password)

        `when`(userService.loadUserByUsername(username)).thenThrow(NotFoundException("User not found"))

        // When & Then
        shouldThrow<BadCredentialsException> {
            authenticationProvider.authenticate(authentication)
        }

        verify(userService).loadUserByUsername(username)
    }

    @Test
    fun `supports should return true for UsernamePasswordAuthenticationToken`() {
        // When
        val supports = authenticationProvider.supports(UsernamePasswordAuthenticationToken::class.java)

        // Then
        supports shouldBe true
    }

    @Test
    fun `supports should return false for other authentication types`() {
        // When
        val supports = authenticationProvider.supports(String::class.java)

        // Then
        supports shouldBe false
    }

    @Test
    fun `authenticate should set correct authorities`() {
        // Given
        val username = "testuser"
        val password = "password123"
        val authentication = UsernamePasswordAuthenticationToken(username, password)

        `when`(userService.loadUserByUsername(username)).thenReturn(testUser)
        `when`(passwordEncoder.matches(password, testUser.password)).thenReturn(true)

        // When
        val result = authenticationProvider.authenticate(authentication)

        // Then
        val authorities = result.authorities
        authorities.size shouldBe 1
        authorities.any { it.authority == "USER" } shouldBe true
    }
}
