package com.github.logboard.log.service

import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.repository.ClickHouseLogRepository
import org.springframework.stereotype.Service

@Service
class TimelineService(
    private val clickHouseLogRepository: ClickHouseLogRepository
) {

    fun getTimeline(request: TimelineRequest): List<TimelineItem> {
        val fromMs = request.from.toEpochMilli()
        val toMs = request.to.toEpochMilli()
        val bucketMs = calculateBucketMs(toMs - fromMs)

        return clickHouseLogRepository.timeline(
            projectId = request.projectId.toString(),
            from = fromMs,
            to = toMs,
            bucketMs = bucketMs,
            levels = request.level,
            message = request.message
        )
    }

    private fun calculateBucketMs(rangeMs: Long): Long = when {
        rangeMs <= 3_600_000L    -> 60_000L       // ≤ 1h  → 1 min
        rangeMs <= 21_600_000L   -> 300_000L      // ≤ 6h  → 5 min
        rangeMs <= 86_400_000L   -> 1_800_000L    // ≤ 24h → 30 min
        rangeMs <= 604_800_000L  -> 3_600_000L    // ≤ 7d  → 1 hour
        else                     -> 21_600_000L   // > 7d  → 6 hours
    }
}
