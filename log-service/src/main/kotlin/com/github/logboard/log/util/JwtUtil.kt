package com.github.logboard.log.util

import com.github.logboard.log.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(private val jwtProperties: JwtProperties) {

    private companion object {
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

    fun isTokenValid(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token) ?: return false
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            logger.debug("Token validation failed: ${e.message}")
            false
        }
    }

    private fun extractAllClaims(token: String): Claims? {
        return try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: JwtException) {
            logger.debug("JWT parsing failed: ${e.message}")
            null
        }
    }
}
