package com.github.logboard.log.config

import com.github.logboard.log.security.ApiKeyAuthenticationFilter
import com.github.logboard.log.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity) =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize.anyRequest().authenticated()
            }
            .exceptionHandling { exception ->
                exception.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")
                }
            }
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
