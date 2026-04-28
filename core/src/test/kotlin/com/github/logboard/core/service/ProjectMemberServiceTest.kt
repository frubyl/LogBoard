package com.github.logboard.core.service

import com.github.logboard.core.dto.ProjectMemberAddRequest
import com.github.logboard.core.dto.ProjectMemberRoleUpdateRequest
import com.github.logboard.core.exception.common.AlreadyExistsException
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import com.github.logboard.core.model.Project
import com.github.logboard.core.model.ProjectMember
import com.github.logboard.core.model.ProjectMemberId
import com.github.logboard.core.model.ProjectRole
import com.github.logboard.core.model.User
import com.github.logboard.core.repository.ProjectMemberRepository
import com.github.logboard.core.repository.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ProjectMemberServiceTest {

    @Mock private lateinit var projectRepository: ProjectRepository
    @Mock private lateinit var projectMemberRepository: ProjectMemberRepository
    @Mock private lateinit var userService: UserService

    @InjectMocks private lateinit var projectMemberService: ProjectMemberService

    private lateinit var actor: User
    private lateinit var targetUser: User
    private lateinit var project: Project
    private lateinit var ownerMember: ProjectMember
    private lateinit var adminMember: ProjectMember
    private lateinit var readerMember: ProjectMember

    @BeforeEach
    fun setUp() {
        actor = User(id = 1L, username = "actor", password = "pass",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        targetUser = User(id = 2L, username = "target", password = "pass",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        project = Project(id = UUID.randomUUID(), name = "Test Project",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())

        ownerMember = ProjectMember(
            id = ProjectMemberId(project.id, actor.id), project = project,
            user = actor, role = ProjectRole.OWNER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        adminMember = ProjectMember(
            id = ProjectMemberId(project.id, actor.id), project = project,
            user = actor, role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        readerMember = ProjectMember(
            id = ProjectMemberId(project.id, actor.id), project = project,
            user = actor, role = ProjectRole.READER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
    }

    // --- getMembers ---

    @Test
    fun `getMembers should return list when actor is OWNER`() {
        val projectId = project.id!!
        val members = listOf(
            ProjectMember(id = ProjectMemberId(projectId, actor.id), project = project,
                user = actor, role = ProjectRole.OWNER,
                createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()),
            ProjectMember(id = ProjectMemberId(projectId, targetUser.id), project = project,
                user = targetUser, role = ProjectRole.READER,
                createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectId(projectId)).thenReturn(members)

        val result = projectMemberService.getMembers(projectId, actor.id!!)

        result.size shouldBe 2
        result[0].userId shouldBe actor.id
        result[0].role shouldBe ProjectRole.OWNER
        result[1].userId shouldBe targetUser.id
        result[1].role shouldBe ProjectRole.READER
    }

    @Test
    fun `getMembers should return list when actor is ADMIN`() {
        val projectId = project.id!!
        val members = listOf(
            ProjectMember(id = ProjectMemberId(projectId, actor.id), project = project,
                user = actor, role = ProjectRole.ADMIN,
                createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(adminMember)
        `when`(projectMemberRepository.findByProjectId(projectId)).thenReturn(members)

        val result = projectMemberService.getMembers(projectId, actor.id!!)

        result.size shouldBe 1
    }

    @Test
    fun `getMembers should throw NotFoundException when project not found`() {
        val projectId = UUID.randomUUID()

        `when`(projectRepository.existsById(projectId)).thenReturn(false)

        val ex = shouldThrow<NotFoundException> {
            projectMemberService.getMembers(projectId, actor.id!!)
        }
        ex.message shouldBe "Project not found with id: $projectId"
    }

    @Test
    fun `getMembers should throw ForbiddenException when actor is not a project member`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.getMembers(projectId, actor.id!!)
        }
        ex.message shouldBe "User is not a member of this project"
    }

    @Test
    fun `getMembers should throw ForbiddenException when actor is READER`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(readerMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.getMembers(projectId, actor.id!!)
        }
        ex.message shouldBe "Only OWNER or ADMIN can view members"
    }

    // --- addMember ---

    @Test
    fun `addMember should succeed when OWNER adds ADMIN`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.ADMIN)
        val savedMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(userService.loadUserByUsername("target")).thenReturn(targetUser)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(null)
        `when`(projectMemberRepository.save(any(ProjectMember::class.java))).thenReturn(savedMember)

        val result = projectMemberService.addMember(projectId, actor.id!!, request)

        result.userId shouldBe targetUser.id
        result.username shouldBe targetUser.username
        result.role shouldBe ProjectRole.ADMIN
        verify(projectMemberRepository).save(any(ProjectMember::class.java))
    }

    @Test
    fun `addMember should succeed when ADMIN adds READER`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.READER)
        val savedMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.READER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(adminMember)
        `when`(userService.loadUserByUsername("target")).thenReturn(targetUser)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(null)
        `when`(projectMemberRepository.save(any(ProjectMember::class.java))).thenReturn(savedMember)

        val result = projectMemberService.addMember(projectId, actor.id!!, request)

        result.role shouldBe ProjectRole.READER
        verify(projectMemberRepository).save(any(ProjectMember::class.java))
    }

    @Test
    fun `addMember should throw ForbiddenException when assigning OWNER role`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.OWNER)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.addMember(projectId, actor.id!!, request)
        }
        ex.message shouldBe "Cannot assign OWNER role when adding a member"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `addMember should throw NotFoundException when project not found`() {
        val projectId = UUID.randomUUID()
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.READER)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.empty())

        val ex = shouldThrow<NotFoundException> {
            projectMemberService.addMember(projectId, actor.id!!, request)
        }
        ex.message shouldBe "Project not found with id: $projectId"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `addMember should throw ForbiddenException when actor is not a project member`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.READER)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.addMember(projectId, actor.id!!, request)
        }
        ex.message shouldBe "User is not a member of this project"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `addMember should throw ForbiddenException when actor is READER`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.READER)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(readerMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.addMember(projectId, actor.id!!, request)
        }
        ex.message shouldBe "Only OWNER or ADMIN can add members"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `addMember should throw ForbiddenException when ADMIN tries to add non-READER`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.ADMIN)

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(adminMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.addMember(projectId, actor.id!!, request)
        }
        ex.message shouldBe "ADMIN can only add members with READER role"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `addMember should throw AlreadyExistsException when user is already a member`() {
        val projectId = project.id!!
        val request = ProjectMemberAddRequest(username = "target", role = ProjectRole.READER)
        val existingMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.READER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.findById(projectId)).thenReturn(Optional.of(project))
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(userService.loadUserByUsername("target")).thenReturn(targetUser)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(existingMember)

        val ex = shouldThrow<AlreadyExistsException> {
            projectMemberService.addMember(projectId, actor.id!!, request)
        }
        ex.message shouldBe "User 'target' is already a member of this project"
        verify(projectMemberRepository, never()).save(any())
    }

    // --- removeMember ---

    @Test
    fun `removeMember should succeed when OWNER removes ADMIN`() {
        val projectId = project.id!!
        val targetAdminMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetAdminMember)
        doNothing().`when`(projectMemberRepository).delete(targetAdminMember)

        projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)

        verify(projectMemberRepository).delete(targetAdminMember)
    }

    @Test
    fun `removeMember should succeed when ADMIN removes READER`() {
        val projectId = project.id!!
        val targetReaderMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.READER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(adminMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetReaderMember)
        doNothing().`when`(projectMemberRepository).delete(targetReaderMember)

        projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)

        verify(projectMemberRepository).delete(targetReaderMember)
    }

    @Test
    fun `removeMember should throw NotFoundException when project not found`() {
        val projectId = UUID.randomUUID()

        `when`(projectRepository.existsById(projectId)).thenReturn(false)

        val ex = shouldThrow<NotFoundException> {
            projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)
        }
        ex.message shouldBe "Project not found with id: $projectId"
        verify(projectMemberRepository, never()).delete(any())
    }

    @Test
    fun `removeMember should throw ForbiddenException when actor is not a project member`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)
        }
        ex.message shouldBe "User is not a member of this project"
        verify(projectMemberRepository, never()).delete(any())
    }

    @Test
    fun `removeMember should throw ForbiddenException when actor is READER`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(readerMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)
        }
        ex.message shouldBe "Only OWNER or ADMIN can remove members"
        verify(projectMemberRepository, never()).delete(any())
    }

    @Test
    fun `removeMember should throw NotFoundException when target is not a project member`() {
        val projectId = project.id!!

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(null)

        val ex = shouldThrow<NotFoundException> {
            projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)
        }
        ex.message shouldBe "User ${targetUser.id} is not a member of project $projectId"
        verify(projectMemberRepository, never()).delete(any())
    }

    @Test
    fun `removeMember should throw ForbiddenException when trying to remove OWNER`() {
        val projectId = project.id!!
        val targetOwnerMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.OWNER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetOwnerMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)
        }
        ex.message shouldBe "Cannot remove the project OWNER"
        verify(projectMemberRepository, never()).delete(any())
    }

    @Test
    fun `removeMember should throw ForbiddenException when ADMIN tries to remove non-READER`() {
        val projectId = project.id!!
        val targetAdminMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(adminMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetAdminMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.removeMember(projectId, actor.id!!, targetUser.id!!)
        }
        ex.message shouldBe "ADMIN can only remove members with READER role"
        verify(projectMemberRepository, never()).delete(any())
    }

    // --- updateMemberRole ---

    @Test
    fun `updateMemberRole should succeed when OWNER changes ADMIN to READER`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)
        val targetAdminMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.ADMIN,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetAdminMember)
        `when`(projectMemberRepository.save(targetAdminMember)).thenReturn(targetAdminMember)

        val result = projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)

        result.userId shouldBe targetUser.id
        result.role shouldBe ProjectRole.READER
        verify(projectMemberRepository).save(targetAdminMember)
    }

    @Test
    fun `updateMemberRole should succeed when OWNER changes READER to ADMIN`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.ADMIN)
        val targetReaderMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.READER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetReaderMember)
        `when`(projectMemberRepository.save(targetReaderMember)).thenReturn(targetReaderMember)

        val result = projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)

        result.role shouldBe ProjectRole.ADMIN
        verify(projectMemberRepository).save(targetReaderMember)
    }

    @Test
    fun `updateMemberRole should throw ForbiddenException when assigning OWNER role`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.OWNER)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)
        }
        ex.message shouldBe "Cannot assign OWNER role"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `updateMemberRole should throw NotFoundException when project not found`() {
        val projectId = UUID.randomUUID()
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)

        `when`(projectRepository.existsById(projectId)).thenReturn(false)

        val ex = shouldThrow<NotFoundException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)
        }
        ex.message shouldBe "Project not found with id: $projectId"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `updateMemberRole should throw ForbiddenException when actor is not a project member`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(null)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)
        }
        ex.message shouldBe "User is not a member of this project"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `updateMemberRole should throw ForbiddenException when actor is not OWNER`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(adminMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)
        }
        ex.message shouldBe "Only OWNER can change member roles"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `updateMemberRole should throw ForbiddenException when actor tries to change own role`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, actor.id!!, request)
        }
        ex.message shouldBe "Cannot change your own role"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `updateMemberRole should throw NotFoundException when target is not a project member`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(null)

        val ex = shouldThrow<NotFoundException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)
        }
        ex.message shouldBe "User ${targetUser.id} is not a member of project $projectId"
        verify(projectMemberRepository, never()).save(any())
    }

    @Test
    fun `updateMemberRole should throw ForbiddenException when trying to change OWNER role`() {
        val projectId = project.id!!
        val request = ProjectMemberRoleUpdateRequest(role = ProjectRole.READER)
        val targetOwnerMember = ProjectMember(
            id = ProjectMemberId(projectId, targetUser.id), project = project,
            user = targetUser, role = ProjectRole.OWNER,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )

        `when`(projectRepository.existsById(projectId)).thenReturn(true)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id!!)).thenReturn(ownerMember)
        `when`(projectMemberRepository.findByProjectIdAndUserId(projectId, targetUser.id!!)).thenReturn(targetOwnerMember)

        val ex = shouldThrow<ForbiddenException> {
            projectMemberService.updateMemberRole(projectId, actor.id!!, targetUser.id!!, request)
        }
        ex.message shouldBe "Cannot change the role of the project OWNER"
        verify(projectMemberRepository, never()).save(any())
    }
}
