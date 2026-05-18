package com.github.logboard.log.config

import com.github.logboard.log.model.LogDocumentEs
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Component

@Component
class ElasticsearchSchemaInitializer(
    private val elasticsearchOperations: ElasticsearchOperations
) {

    private val logger = LoggerFactory.getLogger(ElasticsearchSchemaInitializer::class.java)

    @PostConstruct
    fun initialize() {
        val indexOps = elasticsearchOperations.indexOps(LogDocumentEs::class.java)
        if (!indexOps.exists()) {
            indexOps.createWithMapping()
            logger.info("Elasticsearch index 'logs' created with mapping")
        }
    }
}
