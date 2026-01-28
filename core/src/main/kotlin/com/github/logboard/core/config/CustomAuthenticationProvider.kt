package com.github.logboard.core.config

import com.github.logboard.core.model.User
import com.github.logboard.core.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationProvider(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationProvider {

    companion object {
        private val logger = LoggerFactory.getLogger(CustomAuthenticationProvider::class.java)
    }

    override fun authenticate(authentication: Authentication): Authentication {
        val username = authentication.name
        val password = authentication.credentials.toString()

        logger.debug("Attempting authentication for user: $username")

        val user: User = try {
            userService.loadUserByUsername(username)
        } catch (e: Exception) {
            logger.error("User not found: $username", e)
            throw BadCredentialsException("Invalid username or password")
        }

        if (passwordEncoder.matches(password, user.password)) {
            logger.info("Authentication successful for user: $username")
            return UsernamePasswordAuthenticationToken(
                user,
                password,
                AuthorityUtils.createAuthorityList("USER")
            )
        }

        logger.warn("Authentication failed for user: $username - invalid password")
        throw BadCredentialsException("Invalid username or password")
    }

    override fun supports(authentication: Class<*>): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
    }
}
