package io.robothouse.agent.repository

import io.robothouse.agent.entity.Identity
import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data repository for the singleton identity configuration. */
interface IdentityRepository : JpaRepository<Identity, Int>
