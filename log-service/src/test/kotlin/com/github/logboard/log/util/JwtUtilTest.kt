package com.github.logboard.log.util

import com.github.logboard.log.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Date

class JwtUtilTest : DescribeSpec({

    val secret = "mySecretKeyForLogBoardApplicationWhichIsVeryLongAndSecure123456789012345"
    val jwtProperties = JwtProperties(secret = secret)
    val jwtUtil = JwtUtil(jwtProperties)

    val signingKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun createToken(userId: Long, expiresIn: Long = 3_600_000L): String =
        Jwts.builder()
            .claim("id", userId)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expiresIn))
            .signWith(signingKey, SignatureAlgorithm.HS512)
            .compact()

    describe("extractUserId") {

        it("извлекает userId типа Long из токена") {
            val token = createToken(42L)

            val result = jwtUtil.extractUserId(token)

            result shouldBe 42L
        }

        it("возвращает null для невалидного токена") {
            val result = jwtUtil.extractUserId("not.a.valid.jwt")

            result shouldBe null
        }

        it("возвращает null для просроченного токена") {
            val token = createToken(1L, expiresIn = -1000L)

            val result = jwtUtil.extractUserId(token)

            result shouldBe null
        }

        it("возвращает null для пустой строки") {
            val result = jwtUtil.extractUserId("")

            result shouldBe null
        }
    }

    describe("isTokenValid") {

        it("возвращает true для валидного и не просроченного токена") {
            val token = createToken(7L)

            jwtUtil.isTokenValid(token) shouldBe true
        }

        it("возвращает false для просроченного токена") {
            val token = createToken(7L, expiresIn = -1000L)

            jwtUtil.isTokenValid(token) shouldBe false
        }

        it("возвращает false для невалидного токена") {
            jwtUtil.isTokenValid("garbage.token.value") shouldBe false
        }

        it("возвращает false для пустой строки") {
            jwtUtil.isTokenValid("") shouldBe false
        }
    }
})
