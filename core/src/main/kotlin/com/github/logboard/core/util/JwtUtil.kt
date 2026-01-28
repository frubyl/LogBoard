package com.github.logboard.core.util

import com.github.logboard.core.config.JwtProperties
import com.github.logboard.core.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(
    private val jwtProperties: JwtProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtUtil::class.java)
    }

    private val secretKey: SecretKey by lazy {
        val keyBytes = jwtProperties.secret.toByteArray()
        if (keyBytes.size >= 64) {
            Keys.hmacShaKeyFor(keyBytes)
        } else {
            val sha512Bytes = java.security.MessageDigest.getInstance("SHA-512").digest(keyBytes)
            Keys.hmacShaKeyFor(sha512Bytes)
        }
    }

    fun generateAccessToken(user: User): String =
        generateToken(user, jwtProperties.accessTokenExpiration)

    fun generateRefreshToken(user: User): String =
        generateToken(user, jwtProperties.refreshTokenExpiration)

    fun validateToken(token: String, user: User): Boolean {
        return try {
            val claims = extractAllClaims(token) ?: return false
            val id = claims["id"]
            val userId = when (id) {
                is Int -> id.toLong()
                is Long -> id
                else -> return false
            }
            userId == user.id && !claims.expiration.before(Date())
        } catch (e: Exception) {
            logger.error("Token validation failed", e)
            false
        }
    }

    fun extractUserId(token: String): Long {
        val claims = extractAllClaims(token)
            ?: throw IllegalArgumentException("Invalid JWT token")
        val id = claims["id"]
        return when (id) {
            is Int -> id.toLong()
            is Long -> id
            else -> throw IllegalArgumentException("Invalid user ID type in JWT token")
        }
    }

    private fun generateToken(user: User, expirationTime: Long): String {
        val userId = user.id ?: throw IllegalArgumentException("User ID is null")
        return Jwts.builder()
            .claim("id", userId)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationTime))
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact()
    }

    private fun extractAllClaims(token: String): Claims? {
        return try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: JwtException) {
            logger.error("JWT parsing failed: ${e.message}")
            null
        }
    }

    private fun isTokenExpired(token: String): Boolean {
        val claims = extractAllClaims(token) ?: return true
        return claims.expiration.before(Date())
    }
}
