package com.github.logboard.log.repository

import com.github.logboard.log.model.LogDocument
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.jdbc.core.RowMapper

class ClickHouseLogRepositoryTest : DescribeSpec({

    val jdbcTemplate = mock<JdbcTemplate>()
    val repository = ClickHouseLogRepository(jdbcTemplate)

    beforeEach {
        clearInvocations(jdbcTemplate)
    }

    describe("bulkInsert") {
        it("вызывает batchUpdate с правильными параметрами") {
            val docs = listOf(
                LogDocument(
                    id = "id-1",
                    projectId = "proj-1",
                    ingestionId = "ing-1",
                    level = "INFO",
                    message = "hello",
                    timestamp = 1000L
                ),
                LogDocument(
                    id = "id-2",
                    projectId = "proj-1",
                    ingestionId = "ing-1",
                    level = "ERROR",
                    message = "oops",
                    timestamp = 2000L
                )
            )

            repository.bulkInsert(docs)

            verify(jdbcTemplate).batchUpdate(any<String>(), any<List<Array<Any>>>())
        }

        it("ничего не делает при пустом списке") {
            repository.bulkInsert(emptyList())

            verify(jdbcTemplate, never()).batchUpdate(any<String>(), any<List<Array<Any>>>())
        }
    }

    describe("timeline") {
        it("выполняет GROUP BY bucket, level запрос") {
            whenever(jdbcTemplate.query(any<String>(), any<PreparedStatementSetter>(), any<RowMapper<Any>>()))
                .thenReturn(emptyList())

            repository.timeline("proj-1", from = 0L, to = 1000L, bucketMs = 60000L, level = null)

            verify(jdbcTemplate).query(
                argThat { sql -> sql.contains("GROUP BY bucket, level") },
                any<PreparedStatementSetter>(),
                any<RowMapper<Any>>()
            )
        }

        it("использует intDiv для группировки по бакетам") {
            whenever(jdbcTemplate.query(any<String>(), any<PreparedStatementSetter>(), any<RowMapper<Any>>()))
                .thenReturn(emptyList())

            repository.timeline("proj-1", 0L, 1000L, 60000L, null)

            verify(jdbcTemplate).query(
                argThat { sql -> sql.contains("intDiv(timestamp, ?)") },
                any<PreparedStatementSetter>(),
                any<RowMapper<Any>>()
            )
        }

        it("фильтрует по project_id и временному диапазону") {
            whenever(jdbcTemplate.query(any<String>(), any<PreparedStatementSetter>(), any<RowMapper<Any>>()))
                .thenReturn(emptyList())

            repository.timeline("proj-1", 100L, 900L, 60000L, null)

            verify(jdbcTemplate).query(
                argThat { sql ->
                    sql.contains("project_id = ?") &&
                        sql.contains("timestamp >= ?") &&
                        sql.contains("timestamp <= ?")
                },
                any<PreparedStatementSetter>(),
                any<RowMapper<Any>>()
            )
        }

        it("добавляет фильтр по level при указании уровня") {
            whenever(jdbcTemplate.query(any<String>(), any<PreparedStatementSetter>(), any<RowMapper<Any>>()))
                .thenReturn(emptyList())

            repository.timeline("proj-1", 0L, 1000L, 60000L, "ERROR")

            verify(jdbcTemplate).query(
                argThat { sql -> sql.contains("level = ?") },
                any<PreparedStatementSetter>(),
                any<RowMapper<Any>>()
            )
        }

        it("не добавляет фильтр по level при отсутствии уровня") {
            whenever(jdbcTemplate.query(any<String>(), any<PreparedStatementSetter>(), any<RowMapper<Any>>()))
                .thenReturn(emptyList())

            repository.timeline("proj-1", 0L, 1000L, 60000L, null)

            verify(jdbcTemplate).query(
                argThat { sql -> !sql.contains("level = ?") },
                any<PreparedStatementSetter>(),
                any<RowMapper<Any>>()
            )
        }
    }
})
