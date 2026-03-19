package io.robothouse.agent.tool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.service.SkillCacheService
import io.robothouse.agent.service.SkillService
import io.robothouse.agent.util.log
import org.springframework.stereotype.Component

/**
 * Delegation function signature for executing a request against a target skill.
 * Accepts the target skill, the request text, the current delegation depth,
 * an event listener, and an optional conversation ID.
 */
typealias DelegateFn = (Skill, String, Int, AgentEventListener, String?) -> AgentResponse

/**
 * Factory for the `delegateToSkill` meta-tool that enables skill-to-skill
 * handoff at runtime. Creates a [ToolSpecification] and depth-aware
 * [ToolExecutor] for each agent loop invocation.
 *
 * This component is not annotated with `@Tool` and is invisible to
 * [io.robothouse.agent.repository.ToolRepository]. The meta-tool is
 * injected into every skill's tool specifications by
 * [io.robothouse.agent.service.DynamicAgentService] at execution time.
 */
@Component
class DelegateToSkillExecutorFactory(
    private val skillService: SkillService,
    private val skillCacheService: SkillCacheService
) {

    companion object {
        const val TOOL_NAME = "delegateToSkill"
        private val objectMapper = jacksonObjectMapper()
    }

    /**
     * Returns the tool specification for `delegateToSkill` with `skillName`
     * and `request` parameters. The description includes the list of available
     * skills and their tools, so the LLM knows what it can delegate to.
     */
    fun specification(): ToolSpecification {
        val skillDescriptions = skillCacheService.findAll().joinToString("\n") { skill ->
            val tools = if (skill.toolNames.isNotEmpty()) " (tools: ${skill.toolNames.joinToString(", ")})" else ""
            "- ${skill.name}: ${skill.description}$tools"
        }

        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(
                "Delegates a request to a DIFFERENT skill. Use this only when the current task " +
                    "requires capabilities from another skill that you do not have. Do NOT delegate " +
                    "to your own skill. The target skill will execute the request using its own tools " +
                    "and expertise, and return the result. The skillName must exactly match one of " +
                    "the available skills listed below.\n\n" +
                    "Available skills:\n$skillDescriptions"
            )
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("skillName", "The exact name of the target skill to delegate to")
                    .addStringProperty("request", "The request to send to the target skill")
                    .required(listOf("skillName", "request"))
                    .build()
            )
            .build()
    }

    /**
     * Creates a depth-aware [ToolExecutor] for `delegateToSkill`. When invoked,
     * the executor resolves the target skill, emits handoff events, and calls
     * the [delegateFn] to execute a nested agent loop. Returns an error string
     * if the depth limit is exceeded or the target skill is not found.
     */
    fun createExecutor(
        currentDepth: Int,
        maxDepth: Int,
        currentSkillName: String,
        listener: AgentEventListener,
        conversationId: String?,
        delegateFn: DelegateFn
    ): ToolExecutor {
        return ToolExecutor { request, _ ->
            if (currentDepth >= maxDepth) {
                return@ToolExecutor "Delegation depth limit reached (max: $maxDepth). Cannot delegate further."
            }

            val args: Map<String, String> = try {
                objectMapper.readValue(request.arguments())
            } catch (e: Exception) {
                return@ToolExecutor "Invalid arguments for delegateToSkill: ${e.message}"
            }

            val skillName = args["skillName"]
                ?: return@ToolExecutor "Missing required parameter: skillName"
            val delegationRequest = args["request"]
                ?: return@ToolExecutor "Missing required parameter: request"

            if (skillName == currentSkillName) {
                return@ToolExecutor "Cannot delegate to yourself ('$skillName'). You already are this skill — handle the request directly."
            }

            val targetSkill = skillService.findByName(skillName)
                ?: return@ToolExecutor "Skill '$skillName' not found. Available skills can be viewed via the skills API."

            log.info { "Skill handoff: $currentSkillName -> $skillName (depth=$currentDepth)" }
            listener.onEvent(
                AgentEvent.SkillHandoffStartedEvent(
                    fromSkill = currentSkillName,
                    toSkill = skillName,
                    request = delegationRequest,
                    delegationDepth = currentDepth
                )
            )

            try {
                val response = delegateFn(targetSkill, delegationRequest, currentDepth + 1, listener, conversationId)
                listener.onEvent(
                    AgentEvent.SkillHandoffCompletedEvent(
                        fromSkill = currentSkillName,
                        toSkill = skillName,
                        delegationDepth = currentDepth,
                        success = true
                    )
                )
                response.response
            } catch (e: Exception) {
                log.warn { "Skill handoff failed: $currentSkillName -> $skillName, error: ${e.message}" }
                listener.onEvent(
                    AgentEvent.SkillHandoffCompletedEvent(
                        fromSkill = currentSkillName,
                        toSkill = skillName,
                        delegationDepth = currentDepth,
                        success = false
                    )
                )
                "Delegation to skill '$skillName' failed: ${e.message}"
            }
        }
    }
}
