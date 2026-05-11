package com.github.logboard.log.client

import com.github.logboard.log.dto.MembershipResponse
import com.github.logboard.log.model.MembershipResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Component
class CoreServiceClientImpl(
    @Value("\${core.service.url}") private val coreServiceUrl: String
) : CoreServiceClient {

    companion object {
        private val logger = LoggerFactory.getLogger(CoreServiceClientImpl::class.java)
    }

    private val restClient = RestClient.create()

    override fun getMembership(projectId: UUID, token: String): MembershipResult {
        return try {
            val role = restClient.get()
                .uri("$coreServiceUrl/internal/projects/$projectId/membership")
                .header("Cookie", "access_token=$token")
                .retrieve()
                .body(MembershipResponse::class.java)
                ?.role
            if (role != null) MembershipResult.Found(role) else MembershipResult.Unavailable
        } catch (e: RestClientResponseException) {
            if (e.statusCode.is4xxClientError) {
                logger.debug("Core service returned 4xx for project $projectId: ${e.statusCode}")
                MembershipResult.NotMember
            } else {
                logger.warn("Core service server error for project $projectId: ${e.statusCode}")
                MembershipResult.Unavailable
            }
        } catch (e: Exception) {
            logger.warn("Failed to reach core service for project $projectId: ${e.message}")
            MembershipResult.Unavailable
        }
    }
}
