package com.github.logboard.log.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Configuration
@Profile("!test")
class PostgreSQLConfig {

    @Bean("dataSource")
    @Primary
    fun dataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String
    ): DataSource = HikariDataSource().apply {
        jdbcUrl = url
        this.username = username
        this.password = password
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        minimumIdle = 2
        poolName = "postgresql-pool"
    }
}
