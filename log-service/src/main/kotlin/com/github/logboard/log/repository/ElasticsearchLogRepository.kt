package com.github.logboard.log.repository

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.Time
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField
import co.elastic.clients.json.JsonData
import com.github.logboard.log.model.LogDocument
import com.github.logboard.log.model.LogLevel
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.query.IndexQuery
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

data class TimelineRow(
    val timestamp: LocalDateTime,
    val totalCount: Long,
    val errorCount: Long,
    val warnCount: Long
)

@Repository
class ElasticsearchLogRepository(
    private val operations: ElasticsearchOperations,
    private val esClient: ElasticsearchClient
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(ElasticsearchLogRepository::class.java)
        private const val INDEX = "logs"
    }

    @PostConstruct
    fun initIndex() {
        val indexOps = operations.indexOps(LogDocument::class.java)
        if (!indexOps.exists()) {
            indexOps.createWithMapping()
            logger.info("Elasticsearch index '$INDEX' created")
        }
    }

    fun bulkIndex(docs: List<LogDocument>) {
        if (docs.isEmpty()) return
        val queries = docs.map { doc ->
            IndexQuery().apply {
                id = doc.id
                `object` = doc
            }
        }
        operations.bulkIndex(queries, LogDocument::class.java)
        logger.debug("Indexed ${docs.size} documents into Elasticsearch")
    }

    fun search(
        projectId: UUID,
        from: LocalDateTime,
        to: LocalDateTime,
        levels: List<LogLevel>?,
        message: String?,
        cursor: LocalDateTime?,
        size: Int
    ): List<LogDocument> {
        val query = NativeQuery.builder()
            .withQuery(buildBoolQuery(projectId, from, to, levels, message, cursor))
            .withSort(Sort.by(Sort.Direction.DESC, "timestamp"))
            .withPageable(PageRequest.of(0, size))
            .build()

        return operations.search(query, LogDocument::class.java)
            .searchHits
            .map { it.content }
    }

    fun count(
        projectId: UUID,
        from: LocalDateTime,
        to: LocalDateTime,
        levels: List<LogLevel>?,
        message: String?
    ): Long {
        val query = NativeQuery.builder()
            .withQuery(buildBoolQuery(projectId, from, to, levels, message, null))
            .build()
        return operations.count(query, LogDocument::class.java)
    }

    fun timeline(
        projectId: UUID,
        from: LocalDateTime,
        to: LocalDateTime,
        levels: List<LogLevel>?,
        message: String?
    ): List<TimelineRow> {
        val intervalSeconds = calculateIntervalSeconds(from, to)
        val filterQuery = buildBoolQuery(projectId, from, to, levels, message, null)

        val response = esClient.search({ s ->
            s.index(INDEX)
                .query(filterQuery)
                .size(0)
                .aggregations("buckets") { a ->
                    a.aggregations("errors") { sub ->
                        sub.filter(TermQuery.of { t -> t.field("level").value("ERROR") }._toQuery())
                    }
                    .aggregations("warns") { sub ->
                        sub.filter(TermQuery.of { t -> t.field("level").value("WARN") }._toQuery())
                    }
                    .dateHistogram { dh ->
                        dh.field("timestamp")
                            .fixedInterval(Time.of { t -> t.time("${intervalSeconds}s") })
                    }
                }
        }, LogDocument::class.java)

        return response.aggregations()["buckets"]!!.dateHistogram().buckets().array().map { bucket ->
            TimelineRow(
                timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(bucket.key()), ZoneOffset.UTC),
                totalCount = bucket.docCount(),
                errorCount = bucket.aggregations()["errors"]!!.filter().docCount(),
                warnCount = bucket.aggregations()["warns"]!!.filter().docCount()
            )
        }
    }

    private fun buildBoolQuery(
        projectId: UUID,
        from: LocalDateTime,
        to: LocalDateTime,
        levels: List<LogLevel>?,
        message: String?,
        cursor: LocalDateTime?
    ): Query {
        val filters = mutableListOf<Query>()

        filters.add(TermQuery.of { t -> t.field("project_id").value(projectId.toString()) }._toQuery())
        filters.add(
            RangeQuery.of { r ->
                r.field("timestamp")
                    .gte(JsonData.of(from.toEpochMilli()))
                    .lte(JsonData.of(to.toEpochMilli()))
            }._toQuery()
        )

        if (!levels.isNullOrEmpty()) {
            filters.add(
                TermsQuery.of { t ->
                    t.field("level").terms(
                        TermsQueryField.of { tv -> tv.value(levels.map { FieldValue.of(it.name) }) }
                    )
                }._toQuery()
            )
        }

        if (!message.isNullOrBlank()) {
            filters.add(MatchQuery.of { m -> m.field("message").query(message) }._toQuery())
        }

        if (cursor != null) {
            filters.add(
                RangeQuery.of { r -> r.field("timestamp").lt(JsonData.of(cursor.toEpochMilli())) }._toQuery()
            )
        }

        return BoolQuery.of { b -> b.filter(filters) }._toQuery()
    }

    private fun LocalDateTime.toEpochMilli(): Long =
        this.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun calculateIntervalSeconds(from: LocalDateTime, to: LocalDateTime): Long {
        val durationSeconds = ChronoUnit.SECONDS.between(from, to)
        return when {
            durationSeconds <= 3_600 -> 60
            durationSeconds <= 86_400 -> 3_600
            durationSeconds <= 604_800 -> 86_400
            else -> 604_800
        }
    }
}
