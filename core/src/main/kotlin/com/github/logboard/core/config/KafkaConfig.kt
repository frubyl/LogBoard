package com.github.logboard.core.config

import com.github.logboard.core.event.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {

    @Bean
    fun apiKeysTopic(): NewTopic =
        TopicBuilder.name(KafkaTopics.API_KEYS).partitions(1).replicas(1).build()

    @Bean
    fun projectMembersTopic(): NewTopic =
        TopicBuilder.name(KafkaTopics.PROJECT_MEMBERS).partitions(1).replicas(1).build()
}
