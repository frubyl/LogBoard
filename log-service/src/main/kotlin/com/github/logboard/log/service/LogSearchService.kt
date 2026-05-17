package com.github.logboard.log.service

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField
import co.elastic.clients.json.JsonData
import com.github.logboard.log.dto.LogEntry
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.model.LogDocumentEs
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.NoSuchIndexException
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class LogSearchService(
    private val elasticsearchOperations: ElasticsearchOperations
) {

    fun search(request: LogSearchRequest): LogSearchResponse {
        val filters = mutableListOf<Query>()

        filters += Query.of { q -> q.term { t -> t.field("projectId").value(request.projectId.toString()) } }

        request.level?.takeIf { it.isNotEmpty() }?.let { levels ->
            filters += Query.of { q ->
                q.terms { t ->
                    t.field("level").terms(
                        TermsQueryField.of { tf -> tf.value(levels.map { FieldValue.of(it) }) }
                    )
                }
            }
        }

        request.message?.let { msg ->
            filters += Query.of { q -> q.match { m -> m.field("message").query(msg) } }
        }

        filters += Query.of { q ->
            q.range { r ->
                r.field("timestamp")
                    .gte(JsonData.of(request.from.toEpochMilli()))
                    .lte(JsonData.of(request.to.toEpochMilli()))
            }
        }

        val queryBuilder = NativeQuery.builder()
            .withQuery(Query.of { q -> q.bool { b -> b.filter(filters) } })
            .withPageable(PageRequest.of(0, request.size))
            .withSort(Sort.by(Sort.Direction.DESC, "timestamp"))

        request.cursor?.let { cursor ->
            queryBuilder.withSearchAfter(listOf(cursor.toEpochMilli()))
        }

        return try {
            val hits = elasticsearchOperations.search(queryBuilder.build(), LogDocumentEs::class.java)
            val entries = hits.searchHits.map { it.content.toEntry() }
            val nextCursor = if (entries.size == request.size) entries.last().timestamp else null
            LogSearchResponse(logs = entries, totalCount = hits.totalHits, nextCursor = nextCursor)
        } catch (e: NoSuchIndexException) {
            LogSearchResponse(logs = emptyList(), totalCount = 0)
        }
    }

    private fun LogDocumentEs.toEntry() = LogEntry(
        level = level,
        message = message,
        timestamp = Instant.ofEpochMilli(timestamp)
    )
}
