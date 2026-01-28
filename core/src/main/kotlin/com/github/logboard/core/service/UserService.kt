package com.github.logboard.core.service

import com.github.logboard.core.dto.AuthRequest
import com.github.logboard.core.dto.AuthResponse
import com.github.logboard.core.exception.common.AlreadyExistsException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.exception.authentication.UnauthorizedException
import com.github.logboard.core.model.User
import com.github.logboard.core.repository.UserRepository
import com.github.logboard.core.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil
) {

    companion object {
        private val logger = LoggerFactory.getLogger(UserService::class.java)
    }

    fun registerUser(authRequest: AuthRequest): User {
        if (userRepository.existsByUsername(authRequest.username)) {
            throw AlreadyExistsException("User with username ${authRequest.username} already exists")
        }

        val user = User(
            username = authRequest.username,
            password = passwordEncoder.encode(authRequest.password),
        )

        val savedUser = userRepository.save(user)
        return savedUser
    }

    fun generateTokensForUser(username: String): AuthResponse {
        logger.info("Generating tokens for user: $username")

        val user = loadUserByUsername(username)
        val accessToken = jwtUtil.generateAccessToken(user)
        val refreshToken = jwtUtil.generateRefreshToken(user)

        logger.info("Tokens generated successfully for user: $username")
        return AuthResponse(accessToken, refreshToken)
    }

    fun loadUserByUsername(username: String): User {
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("User not found with username: $username")
        return user
    }

    fun loadUserById(id: Long): User {
        return userRepository.findById(id)
            .orElseThrow { NotFoundException("User not found with id: $id") }
    }

    fun refreshToken(refreshToken: String): AuthResponse {
        logger.info("Attempting to refresh token")

        val userId = jwtUtil.extractUserId(refreshToken)
        val user = loadUserById(userId)

        if (jwtUtil.validateToken(refreshToken, user)) {
            val newAccessToken = jwtUtil.generateAccessToken(user)
            val newRefreshToken = jwtUtil.generateRefreshToken(user)
            logger.info("Token refreshed successfully for user: ${user.username}")
            return AuthResponse(newAccessToken, newRefreshToken)
        } else {
            logger.error("Token refresh failed: invalid refresh token for user id $userId")
            throw UnauthorizedException("Invalid refresh token")
        }
    }
}
