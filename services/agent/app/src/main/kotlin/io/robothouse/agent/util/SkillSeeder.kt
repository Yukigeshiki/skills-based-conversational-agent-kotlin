package io.robothouse.agent.util

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import java.security.MessageDigest
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
            reconcileEmbeddings()
            return
        }

        log.info { "Seeding skills..." }

        val skills = listOf(
            Skill(
                name = "general-assistant",
                description = "A general-purpose assistant that can help with a wide variety of questions and tasks " +
                    "including conversation, knowledge, and analysis. " +
                    "For example: 'What is the current date and time in Tokyo?'",
                systemPrompt = """
                    |You are a helpful assistant. Answer questions concisely and accurately. Use available tools when appropriate.
                    |
                    |## Guidelines
                    |
                    |- **Be concise** — provide clear, direct answers
                    |- **Be accurate** — verify facts before responding
                    |- **Use tools** — leverage available tools when they can help answer the question
                    |- **Be helpful** — if you can't answer directly, suggest next steps
                """.trimMargin(),
                toolNames = listOf("DateTimeTool"),
                isProtected = true
            )
        )

        skills.forEach { skill ->
            val saved = skillRepository.save(skill)
            val embedding = embeddingModel.embed(skill.description).content()
            val skillId = saved.id?.toString() ?: run {
                log.warn { "Skill ID was null after save: name=${saved.name}" }
                throw IllegalStateException("Skill ID was null after save: name=${saved.name}")
            }
            val descriptionHash = sha256(skill.description)
            val metadata = Metadata.from("skillId", skillId).put("descriptionHash", descriptionHash)
            val segment = TextSegment.from(skill.description, metadata)
            embeddingStore.add(embedding, segment)
            log.info { "Seeded skill: ${skill.name}" }
        }

        log.info { "Skill seeding complete" }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Ensures all persisted skills have up-to-date embeddings in the store.
     *
     * Compares a hash of each skill's description against the hash stored
     * in the embedding metadata and only re-embeds when the description has changed
     * or the embedding is missing.
     */
    private fun reconcileEmbeddings() {
        val skills = skillRepository.findAll()
        var reembedded = 0

        for (skill in skills) {
            val skillId = skill.id?.toString() ?: continue
            val descriptionHash = sha256(skill.description)

            val existing = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(skill.description).content())
                    .maxResults(1)
                    .filter(metadataKey("skillId").isEqualTo(skillId))
                    .build()
            ).matches().firstOrNull()

            val existingHash = existing?.embedded()?.metadata()?.getString("descriptionHash")
            if (existing != null && existingHash == descriptionHash) {
                continue
            }

            embeddingStore.removeAll(metadataKey("skillId").isEqualTo(skillId))
            val embedding = embeddingModel.embed(skill.description).content()
            val metadata = Metadata.from("skillId", skillId).put("descriptionHash", descriptionHash)
            val segment = TextSegment.from(skill.description, metadata)
            embeddingStore.add(embedding, segment)
            reembedded++
            log.info { "Re-embedded skill: ${skill.name} (id=$skillId)" }
        }

        if (reembedded > 0) {
            log.info { "Reconciled embeddings: $reembedded skill(s) re-embedded" }
        }
    }
}
