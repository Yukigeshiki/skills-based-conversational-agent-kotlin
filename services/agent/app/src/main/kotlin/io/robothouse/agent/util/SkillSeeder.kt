package io.robothouse.agent.util

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Seeds default skills and their embeddings on application startup
 * when the skills table is empty.
 *
 * Disabled by setting `skill.seeder.enabled=false`.
 */
@Component
@ConditionalOnProperty(name = ["skill.seeder.enabled"], havingValue = "true", matchIfMissing = true)
class SkillSeeder(
    private val skillRepository: SkillRepository,
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        if (skillRepository.count() > 0) {
            log.info { "Skills already seeded, skipping" }
            return
        }

        log.info { "Seeding skills..." }

        val skills = listOf(
            Skill(
                name = "general-assistant",
                description = "A general-purpose assistant that can help with a wide variety of questions and tasks including conversation, knowledge, and analysis.",
                systemPrompt = "You are a helpful assistant. Answer questions concisely and accurately. Use available tools when appropriate.",
                toolNames = listOf("DateTimeTool"),
                planningPrompt = """
                    |You are a task planner. Given a user request and a list of available tools, decompose the request into a structured plan.
                    |
                    |For simple requests that can be answered in one step, return a single-step plan.
                    |For complex requests that require multiple operations, break them into sequential steps.
                    |
                    |Available tools:
                    |{{tools}}
                    |
                    |Respond with ONLY a JSON object in this format:
                    |{
                    |  "reasoning": "Brief explanation of why this plan was chosen",
                    |  "steps": [
                    |    {
                    |      "stepNumber": 1,
                    |      "description": "What to do in this step",
                    |      "expectedTools": ["toolName1"]
                    |    }
                    |  ]
                    |}
                """.trimMargin()
            )
        )

        skills.forEach { skill ->
            val saved = skillRepository.save(skill)
            val embedding = embeddingModel.embed(skill.description).content()
            val skillId = saved.id?.toString() ?: run {
                log.warn { "Skill ID was null after save: name=${saved.name}" }
                throw IllegalStateException("Skill ID was null after save: name=${saved.name}")
            }
            val segment = TextSegment.from(skill.description, Metadata.from("skillId", skillId))
            embeddingStore.add(embedding, segment)
            log.info { "Seeded skill: ${skill.name}" }
        }

        log.info { "Skill seeding complete" }
    }
}
