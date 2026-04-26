package com.github.logboard.core.controller

import com.github.logboard.core.dto.ProjectMemberAddRequest
import com.github.logboard.core.dto.ProjectMemberDto
import com.github.logboard.core.dto.ProjectMemberRoleUpdateRequest
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.model.User
import com.github.logboard.core.service.ProjectMemberService
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProjectMemberControllerTest {

    @Mock private lateinit var projectMemberService: ProjectMemberService

    @InjectMocks private lateinit var projectMemberController: ProjectMemberController

    private val actor = User(id = 1L, username = "actor", password = "pass")
    private val projectId: UUID = UUID.randomUUID()

    @Test
    fun `getMembers should return 200 with list of members`() {
        val members = listOf(
            ProjectMemberDto(userId = 1L, username = "actor", role = ProjectRole.OWNER),
            ProjectMemberDto(userId = 2L, username = "member", role = ProjectRole.READER)
        )

        `when`(projectMemberService.getMembers(projectId, actor.id!!)).thenReturn(members)

        val result = projectMemberController.getMembers(projectId, actor)

        result.statusCode shouldBe HttpStatus.OK
        result.body shouldBe members
        result.body?.size shouldBe 2
        verify(projectMemberService).getMembers(projectId, actor.id!!)
    }

    @Test
    fun `addMember should return 201 with member dto`() {
        val request = ProjectMemberAddRequest(username = "newuser", role = ProjectRole.ADMIN)
        val dto = ProjectMemberDto(userId = 2L, username = "newuser", role = ProjectRole.ADMIN)

        `when`(projectMemberService.addMember(projectId, actor.id!!, request)).thenReturn(dto)

        val result = projectMemberController.addMember(projectId, request, actor)

        result.statusCode shouldBe HttpStatus.CREATED
        result.body shouldBe dto
        result.body?.userId shouldBe 2L
        result.body?.role shouldBe ProjectRole.ADMIN
        verify(projectMemberService).addMember(projectId, actor.id!!, request)
    }

    @Test
    fun `removeMember should return 200`() {
        val targetUserId = 2L

        doNothing().`when`(projectMemberService).removeMember(projectId, actor.id!!, targetUserId)

        val result = projectMemberController.removeMember(projectId, targetUserId, actor)

        result.statusCode shouldBe HttpStatus.OK
        verify(projectMemberService).removeMember(projectId, actor.id!!, targetUserId)
    }

    @Test
    fun `updateMemberRole should return 200 with updated member dto`() {
        val targetUserId = 2L
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)
        val dto = ProjectMemberDto(userId = targetUserId, username = "target", role = ProjectRole.READER)

        `when`(projectMemberService.updateMemberRole(projectId, actor.id!!, targetUserId, request)).thenReturn(dto)

        val result = projectMemberController.updateMemberRole(projectId, targetUserId, request, actor)

        result.statusCode shouldBe HttpStatus.OK
        result.body shouldBe dto
        result.body?.role shouldBe ProjectRole.READER
        verify(projectMemberService).updateMemberRole(projectId, actor.id!!, targetUserId, request)
    }
}
