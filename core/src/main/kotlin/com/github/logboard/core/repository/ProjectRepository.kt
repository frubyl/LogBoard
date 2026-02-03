package com.github.logboard.core.repository

import com.github.logboard.core.model.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProjectRepository : JpaRepository<Project, UUID> {
    @Query("""
        SELECT DISTINCT p FROM Project p
        JOIN p.members m
        WHERE m.user.id = :userId
    """)
    fun findAllByUserId(@Param("userId") userId: Long): List<Project>
}
