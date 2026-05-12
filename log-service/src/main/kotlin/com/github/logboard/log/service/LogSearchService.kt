package com.github.logboard.log.service

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.json.JsonData
import com.github.logboard.log.dto.LogEntry
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.model.LogDocumentEs
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

@Service
class LogSearchService(
    private val elasticsearchOperations: ElasticsearchOperations
) {

    fun search(request: LogSearchRequest): LogSearchResponse {
        val filters = mutableListOf<Query>()

        filters.add(Query.of { q ->
            q.term { t -> t.field("projectId").value(request.projectId.toString()) }
        })
        request.message?.let { msg ->
            filters.add(Query.of { q -> q.match { m -> m.field("message").query(msg) } })
        }
        request.level?.let { lvl ->
            filters.add(Query.of { q ->
                q.term { t -> t.field("level").value(lvl) }
            })
        }
        if (request.from != null || request.to != null) {
            filters.add(Query.of { q ->
                q.range { r ->
                    var b = r.field("timestamp")
                    if (request.from != null) b = b.gte(JsonData.of(request.from))
                    if (request.to != null) b = b.lte(JsonData.of(request.to))
                    b
                }
            })
        }

        val query = NativeQuery.builder()
            .withQuery(Query.of { q -> q.bool { b -> b.filter(filters) } })
            .withPageable(PageRequest.of(request.page, request.size))
            .withSort(Sort.by(Sort.Direction.DESC, "timestamp"))
            .build()

        val hits = elasticsearchOperations.search(query, LogDocumentEs::class.java)
        return LogSearchResponse(
            items = hits.searchHits.map { it.content.toEntry() },
            total = hits.totalHits,
            page = request.page,
            size = request.size
        )
    }

    private fun LogDocumentEs.toEntry() = LogEntry(
        id = id,
        ingestionId = ingestionId,
        level = level,
        message = message,
        timestamp = timestamp
    )
}
