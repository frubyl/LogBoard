package com.github.logboard.log.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class ClickHouseConfig {

    @Bean(name = ["clickHouseDataSource"])
    fun clickHouseDataSource(
        @Value("\${clickhouse.url}") url: String,
        @Value("\${clickhouse.username}") username: String,
        @Value("\${clickhouse.password}") password: String
    ): DataSource = HikariDataSource().apply {
        jdbcUrl = url
        this.username = username
        this.password = password
        driverClassName = "com.clickhouse.jdbc.ClickHouseDriver"
        maximumPoolSize = 5
        minimumIdle = 1
        connectionTimeout = 10_000
        poolName = "clickhouse-pool"
    }

    @Bean(name = ["clickHouseJdbcTemplate"])
    fun clickHouseJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("clickHouseDataSource") ds: DataSource) =
        JdbcTemplate(ds)
}
