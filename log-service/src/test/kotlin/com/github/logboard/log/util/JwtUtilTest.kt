package com.github.logboard.log.util

import com.github.logboard.log.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Date

class JwtUtilTest : DescribeSpec({

    val secret = "mySecretKeyForLogBoardApplicationWhichIsVeryLongAndSecure1234567890"
    val jwtProperties = JwtProperties(secret = secret)
    val jwtUtil = JwtUtil(jwtProperties)

    val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun buildToken(userId: Long, expiredAt: Date = Date(System.currentTimeMillis() + 3_600_000)): String =
        Jwts.builder()
            .claim("id", userId)
            .setExpiration(expiredAt)
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact()

    describe("extractUserId") {
        it("extracts Long user ID from valid token") {
            val token = buildToken(42L)
            jwtUtil.extractUserId(token) shouldBe 42L
        }

        it("throws IllegalArgumentException for invalid token") {
            shouldThrow<IllegalArgumentException> {
                jwtUtil.extractUserId("invalid.token.value")
            }
        }
    }

    describe("isTokenValid") {
        it("returns true for a valid non-expired token") {
            val token = buildToken(1L)
            jwtUtil.isTokenValid(token) shouldBe true
        }

        it("returns false for an expired token") {
            val token = buildToken(1L, expiredAt = Date(System.currentTimeMillis() - 1000))
            jwtUtil.isTokenValid(token) shouldBe false
        }

        it("returns false for a garbage token") {
            jwtUtil.isTokenValid("not.a.jwt") shouldBe false
        }
    }
})
