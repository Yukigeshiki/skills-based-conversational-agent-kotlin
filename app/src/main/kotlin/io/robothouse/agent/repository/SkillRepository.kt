package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SkillRepository : JpaRepository<Skill, UUID> {
    fun findByName(name: String): Skill?
}
