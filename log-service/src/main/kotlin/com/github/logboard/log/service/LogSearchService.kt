package com.github.logboard.log.service

import com.github.logboard.log.dto.LogEntry
import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.dto.LogTimelineRequest
import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.exception.common.ForbiddenException
import com.github.logboard.log.exception.common.NotFoundException
import com.github.logboard.log.model.LogLevel
import com.github.logboard.log.repository.ElasticsearchLogRepository
import com.github.logboard.log.repository.LocalProjectMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class LogSearchService(
    private val elasticsearchLogRepository: ElasticsearchLogRepository,
    private val projectMemberRepository: LocalProjectMemberRepository
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(LogSearchService::class.java)
    }

    fun search(request: LogSearchRequest, userId: Long): LogSearchResponse {
        val projectId = request.projectId!!

        checkProjectAccess(projectId, userId)

        val size = request.size.coerceIn(1, 1000)
        val docs = elasticsearchLogRepository.search(
            projectId = projectId,
            from = request.from!!,
            to = request.to!!,
            levels = request.level,
            message = request.message,
            cursor = request.cursor,
            size = size + 1
        )

        val hasMore = docs.size > size
        val page = if (hasMore) docs.dropLast(1) else docs
        val nextCursor = if (hasMore) page.lastOrNull()?.let { epochToLocalDateTime(it.timestamp) } else null

        val totalCount = elasticsearchLogRepository.count(
            projectId = projectId,
            from = request.from,
            to = request.to,
            levels = request.level,
            message = request.message
        )

        logger.debug("Search for project $projectId returned ${page.size} logs, total: $totalCount")

        return LogSearchResponse(
            logs = page.map { doc ->
                LogEntry(
                    level = LogLevel.valueOf(doc.level),
                    message = doc.message,
                    timestamp = epochToLocalDateTime(doc.timestamp)
                )
            },
            nextCursor = nextCursor,
            totalCount = totalCount
        )
    }

    fun timeline(request: LogTimelineRequest, userId: Long): List<TimelineItem> {
        val projectId = request.projectId!!

        checkProjectAccess(projectId, userId)

        val rows = elasticsearchLogRepository.timeline(
            projectId = projectId,
            from = request.from!!,
            to = request.to!!,
            levels = request.level,
            message = request.message
        )

        logger.debug("Timeline for project $projectId returned ${rows.size} buckets")

        return rows.map {
            TimelineItem(timestamp = it.timestamp, totalCount = it.totalCount, errorCount = it.errorCount, warnCount = it.warnCount)
        }
    }

    private fun checkProjectAccess(projectId: UUID, userId: Long) {
        if (!projectMemberRepository.existsByProjectId(projectId)) {
            throw NotFoundException("Project not found: $projectId")
        }
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ForbiddenException("User $userId is not a member of project $projectId")
    }

    private fun epochToLocalDateTime(epochMilli: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC)
}
