package io.robothouse.agent.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Short-lived cache for skill data, preventing repeated `findAll()` queries
 * during the same chat request. The cache has a short TTL so that skill
 * changes are reflected quickly without explicit invalidation.
 *
 * Both [SkillRouterService] and [TaskPlanningService] use this to avoid
 * hitting the database on every request.
 */
@Service
class SkillCacheService(
    private val skillRepository: SkillRepository
) {

    companion object {
        private const val CACHE_KEY = "all_skills"
        private const val CACHE_TTL_SECONDS = 10L
    }

    private val skillsCache: Cache<String, List<Skill>> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
        .maximumSize(1)
        .build()

    /**
     * Returns all skills, using a short-lived cache to avoid redundant queries.
     */
    fun findAll(): List<Skill> {
        return skillsCache.get(CACHE_KEY) {
            log.debug { "Skills cache miss — loading from database" }
            skillRepository.findAll()
        }
    }

    /**
     * Invalidates the cached skill list. Should be called after skill
     * create, update, or delete operations.
     */
    fun invalidate() {
        log.debug { "Invalidating skills cache" }
        skillsCache.invalidateAll()
    }
}
