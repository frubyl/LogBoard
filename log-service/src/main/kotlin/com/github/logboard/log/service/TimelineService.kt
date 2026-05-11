package com.github.logboard.log.service

import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.dto.TimelineResponse
import com.github.logboard.log.repository.ClickHouseLogRepository
import org.springframework.stereotype.Service

@Service
class TimelineService(
    private val clickHouseLogRepository: ClickHouseLogRepository
) {

    fun getTimeline(request: TimelineRequest): TimelineResponse {
        val buckets = clickHouseLogRepository.timeline(
            projectId = request.projectId.toString(),
            from = request.from,
            to = request.to,
            bucketMs = request.bucketMs,
            level = request.level
        )
        return TimelineResponse(buckets = buckets)
    }
}
