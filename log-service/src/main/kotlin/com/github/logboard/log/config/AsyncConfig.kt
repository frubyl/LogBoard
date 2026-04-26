package com.github.logboard.log.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class AsyncConfig {

    @Bean("logIngestionExecutor")
    fun logIngestionExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 16
        executor.queueCapacity = 500
        executor.setThreadNamePrefix("log-ingestion-")
        executor.initialize()
        return executor
    }
}
