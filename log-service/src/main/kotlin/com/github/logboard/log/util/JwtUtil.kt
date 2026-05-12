package com.github.logboard.log.util

import com.github.logboard.log.config.JwtProperties
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(private val jwtProperties: JwtProperties) {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtUtil::class.java)
    }

    private val secretKey: SecretKey by lazy {
        val keyBytes = jwtProperties.secret.toByteArray()
        if (keyBytes.size >= 64) {
            io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes)
        } else {
            val sha512Bytes = MessageDigest.getInstance("SHA-512").digest(keyBytes)
            io.jsonwebtoken.security.Keys.hmacShaKeyFor(sha512Bytes)
        }
    }

    fun extractUserId(token: String): Long? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .body
            val id = claims["id"]
            when (id) {
                is Int -> id.toLong()
                is Long -> id
                else -> null
            }
        } catch (e: Exception) {
            logger.debug("JWT parsing failed: ${e.message}")
            null
        }
    }

    fun isTokenValid(token: String): Boolean {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .body
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }
}
