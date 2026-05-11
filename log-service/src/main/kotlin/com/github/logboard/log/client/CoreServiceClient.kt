package com.github.logboard.log.client

import com.github.logboard.log.model.MembershipResult
import java.util.UUID

interface CoreServiceClient {
    fun getMembership(projectId: UUID, token: String): MembershipResult
}
