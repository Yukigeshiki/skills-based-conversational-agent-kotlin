package io.robothouse.agent.entity

import io.robothouse.agent.converter.StringListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "skills")
class Skill(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false)
    var name: String = "",

    @Column(nullable = false, length = MAX_DESCRIPTION_LENGTH)
    var description: String = "",

    @Column(name = "system_prompt", nullable = false, length = MAX_SYSTEM_PROMPT_LENGTH)
    var systemPrompt: String = "",

    @Column(name = "tool_names", nullable = false)
    @Convert(converter = StringListConverter::class)
    var toolNames: List<String> = emptyList()
) {
    companion object {
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val MAX_SYSTEM_PROMPT_LENGTH = 4000
    }
}
