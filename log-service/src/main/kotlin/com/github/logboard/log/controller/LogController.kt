package com.github.logboard.log.controller

import com.github.logboard.log.dto.LogSearchRequest
import com.github.logboard.log.dto.LogSearchResponse
import com.github.logboard.log.dto.TimelineItem
import com.github.logboard.log.dto.TimelineRequest
import com.github.logboard.log.exception.ForbiddenException
import com.github.logboard.log.security.AuthenticatedUser
import com.github.logboard.log.service.LogSearchService
import com.github.logboard.log.service.MembershipService
import com.github.logboard.log.service.TimelineService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/logs")
class LogController(
    private val membershipService: MembershipService,
    private val logSearchService: LogSearchService,
    private val timelineService: TimelineService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(LogController::class.java)
        private const val MAX_PAGE_SIZE = 500
    }

    @PostMapping("/search")
    fun search(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: LogSearchRequest
    ): ResponseEntity<LogSearchResponse> {
        logger.debug("Search logs for project ${request.projectId} by user ${principal.userId}")

        membershipService.getMembership(principal.userId, request.projectId, principal.token)
            ?: throw ForbiddenException("User is not a member of project ${request.projectId}")

        val cappedRequest = request.copy(size = request.size.coerceAtMost(MAX_PAGE_SIZE))
        return ResponseEntity.ok(logSearchService.search(cappedRequest))
    }

    @PostMapping("/timeline")
    fun timeline(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody request: TimelineRequest
    ): ResponseEntity<List<TimelineItem>> {
        logger.debug("Timeline for project ${request.projectId} by user ${principal.userId}")

        membershipService.getMembership(principal.userId, request.projectId, principal.token)
            ?: throw ForbiddenException("User is not a member of project ${request.projectId}")

        return ResponseEntity.ok(timelineService.getTimeline(request))
    }
}
