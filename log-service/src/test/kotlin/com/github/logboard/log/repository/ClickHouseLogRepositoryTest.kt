package com.github.logboard.log.repository

import com.github.logboard.log.model.LogDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClickHouseLogRepositoryTest {

    private val jdbcTemplate = mock<JdbcTemplate>()
    private val repository = ClickHouseLogRepository(jdbcTemplate)

    @BeforeEach
    fun setUp() {
        clearInvocations(jdbcTemplate)
        whenever(jdbcTemplate.query(any<String>(), any<PreparedStatementSetter>(), any<RowMapper<Any>>()))
            .thenReturn(emptyList())
    }

    @Test
    fun `calls batchUpdate with correct parameters`() {
        val docs = listOf(
            LogDocument("id-1", "proj-1", "ing-1", "INFO", "hello", 1000L),
            LogDocument("id-2", "proj-1", "ing-1", "ERROR", "oops", 2000L)
        )

        repository.bulkInsert(docs)

        verify(jdbcTemplate).batchUpdate(any<String>(), any<List<Array<Any>>>())
    }

    @Test
    fun `does nothing when list is empty`() {
        repository.bulkInsert(emptyList())

        verify(jdbcTemplate, never()).batchUpdate(any<String>(), any<List<Array<Any>>>())
    }

    @Test
    fun `uses intDiv for bucket grouping`() {
        repository.timeline("proj-1", 0L, 1000L, 60_000L, null, null)

        verify(jdbcTemplate).query(
            argThat { sql -> sql.contains("intDiv(timestamp, ?)") },
            any<PreparedStatementSetter>(), any<RowMapper<Any>>()
        )
    }

    @Test
    fun `groups by bucket only and aggregates error and warn counts`() {
        repository.timeline("proj-1", 0L, 1000L, 60_000L, null, null)

        verify(jdbcTemplate).query(
            argThat { sql ->
                sql.contains("GROUP BY bucket") &&
                    sql.contains("countIf(level = 'ERROR')") &&
                    sql.contains("countIf(level = 'WARN')")
            },
            any<PreparedStatementSetter>(), any<RowMapper<Any>>()
        )
    }

    @Test
    fun `filters by project_id and time range`() {
        repository.timeline("proj-1", 100L, 900L, 60_000L, null, null)

        verify(jdbcTemplate).query(
            argThat { sql ->
                sql.contains("project_id = ?") &&
                    sql.contains("timestamp >= ?") &&
                    sql.contains("timestamp <= ?")
            },
            any<PreparedStatementSetter>(), any<RowMapper<Any>>()
        )
    }

    @Test
    fun `adds IN filter when levels list is provided`() {
        repository.timeline("proj-1", 0L, 1000L, 60_000L, listOf("ERROR", "WARN"), null)

        verify(jdbcTemplate).query(
            argThat { sql -> sql.contains("level IN") },
            any<PreparedStatementSetter>(), any<RowMapper<Any>>()
        )
    }

    @Test
    fun `adds ilike filter when message is provided`() {
        repository.timeline("proj-1", 0L, 1000L, 60_000L, null, "timeout")

        verify(jdbcTemplate).query(
            argThat { sql -> sql.contains("ilike") },
            any<PreparedStatementSetter>(), any<RowMapper<Any>>()
        )
    }

    @Test
    fun `does not add level filter when levels is null`() {
        repository.timeline("proj-1", 0L, 1000L, 60_000L, null, null)

        verify(jdbcTemplate).query(
            argThat { sql -> !sql.contains("level IN") },
            any<PreparedStatementSetter>(), any<RowMapper<Any>>()
        )
    }
}
