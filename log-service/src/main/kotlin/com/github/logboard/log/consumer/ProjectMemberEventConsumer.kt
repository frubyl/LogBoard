package com.github.logboard.log.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.logboard.log.event.ProjectMemberEvent
import com.github.logboard.log.model.LocalProjectMember
import com.github.logboard.log.model.LocalProjectMemberId
import com.github.logboard.log.repository.LocalProjectMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ProjectMemberEventConsumer(
    private val localProjectMemberRepository: LocalProjectMemberRepository,
    private val objectMapper: ObjectMapper
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(ProjectMemberEventConsumer::class.java)
    }

    @KafkaListener(topics = ["logboard.project-members"], groupId = "log-service")
    fun handle(message: String) {
        val event = objectMapper.readValue(message, ProjectMemberEvent::class.java)
        logger.debug("Received ProjectMemberEvent: ${event.eventType} for project ${event.projectId}, user ${event.userId}")

        when (event.eventType) {
            ProjectMemberEvent.EventType.ADDED -> {
                val entity = LocalProjectMember(
                    projectId = event.projectId,
                    userId = event.userId,
                    role = event.role!!
                )
                localProjectMemberRepository.save(entity)
                logger.info("Stored local member userId=${event.userId} in project ${event.projectId} with role ${event.role}")
            }
            ProjectMemberEvent.EventType.REMOVED -> {
                localProjectMemberRepository.deleteById(LocalProjectMemberId(event.projectId, event.userId))
                logger.info("Deleted local member userId=${event.userId} from project ${event.projectId}")
            }
            ProjectMemberEvent.EventType.ROLE_CHANGED -> {
                val existing = localProjectMemberRepository.findByProjectIdAndUserId(event.projectId, event.userId)
                if (existing != null) {
                    existing.role = event.role!!
                    localProjectMemberRepository.save(existing)
                    logger.info("Updated local member userId=${event.userId} in project ${event.projectId} to role ${event.role}")
                }
            }
        }
    }
}
