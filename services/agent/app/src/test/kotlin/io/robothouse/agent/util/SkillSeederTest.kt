package io.robothouse.agent.util

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.store.embedding.EmbeddingStore
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SkillSeederTest {

    private val skillRepository: SkillRepository = mock()
    private val embeddingModel: EmbeddingModel = mock()
    private val embeddingStore: EmbeddingStore<TextSegment> = mock()

    private val seeder = SkillSeeder(skillRepository, embeddingModel, embeddingStore)

    @Test
    fun `seeds skills when repository is empty`() {
        whenever(skillRepository.count()).thenReturn(0)
        val embedding = Embedding.from(FloatArray(1536) { 0.1f })
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(skillRepository.save(any<Skill>())).thenAnswer { invocation ->
            val skill = invocation.getArgument<Skill>(0)
            skill.also { it.id = java.util.UUID.randomUUID() }
        }

        seeder.run(null)

        verify(skillRepository, times(1)).save(any<Skill>())
        verify(embeddingStore, times(1)).add(any<Embedding>(), any<TextSegment>())
    }

    @Test
    fun `skips seeding when skills already exist`() {
        whenever(skillRepository.count()).thenReturn(2)

        seeder.run(null)

        verify(skillRepository, never()).save(any<Skill>())
    }
}
