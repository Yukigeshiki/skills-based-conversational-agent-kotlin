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
                name = "datetime-assistant",
                description = "Helps with date, time, timezone conversions, and clock-related questions. Can tell the current time in any timezone.",
                systemPrompt = "You are a date and time assistant. Help users with timezone conversions, current time queries, and date calculations. Use the DateTimeTool when you need to get the current time.",
                toolNames = listOf("DateTimeTool")
            ),
            Skill(
                name = "general-assistant",
                description = "A general-purpose assistant that can help with a wide variety of questions and tasks including conversation, knowledge, and analysis.",
                systemPrompt = "You are a helpful assistant. Answer questions concisely and accurately. Use available tools when appropriate.",
                toolNames = listOf("DateTimeTool")
            )
        )

        skills.forEach { skill ->
            val saved = skillRepository.save(skill)
            val embedding = embeddingModel.embed(skill.description).content()
            val segment = TextSegment.from(skill.description, Metadata.from("skillId", saved.id.toString()))
            embeddingStore.add(embedding, segment)
            log.info { "Seeded skill: ${skill.name}" }
        }

        log.info { "Skill seeding complete" }
    }
}
