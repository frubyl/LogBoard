package com.github.logboard.log.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import java.util.UUID

class ApiKeyAuthentication(val projectId: UUID) : AbstractAuthenticationToken(emptyList()) {
    init { isAuthenticated = true }
    override fun getCredentials() = null
    override fun getPrincipal(): UUID = projectId
}
