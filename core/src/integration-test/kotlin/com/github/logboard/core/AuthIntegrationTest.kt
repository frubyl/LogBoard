package com.github.logboard.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.repository.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthIntegrationTest : AbstractIntegrationTest() {
    // TODO [test] допокрыть тестами
    // TODO [refactoring] провести рефакторинг функционала аутентификации
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should register user successfully and verify database record`() {
        // Given
        val authRequest = AuthRequest("testuser_unique1", "password123")

        // When & Then
        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }.andExpect {
            status { isCreated() }
        }

        val savedUser = userRepository.findByUsername("testuser_unique1")
        savedUser shouldNotBe null
        savedUser!!.username shouldBe "testuser_unique1"
        savedUser.password shouldNotBe ""
    }

    @Test
    fun `should fail to register user with existing username`() {
        // Given
        val authRequest = AuthRequest("testuser_unique2", "password123")
        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }

        // When & Then
        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { exists() }
        }

        val users = userRepository.findAll().filter { it.username == "testuser_unique2" }
        users shouldHaveSize 1
    }

    @Test
    fun `should authenticate user successfully`() {
        // Given
        val authRequest = AuthRequest("testuser_unique3", "password123")
        mockMvc.post("/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }

        // When & Then
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
        }

        val savedUser = userRepository.findByUsername("testuser_unique3")
        savedUser shouldNotBe null
    }

    @Test
    fun `should fail to authenticate with invalid credentials`() {
        // Given
        val authRequest = AuthRequest("nonexistent", "wrongpass123")

        // When & Then
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(authRequest)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { exists() }
        }

        val savedUser = userRepository.findByUsername("nonexistent")
        savedUser shouldBe null
    }
}
