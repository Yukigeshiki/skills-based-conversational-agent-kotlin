package io.robothouse.agent.graph.orchestration

import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.service.ConversationMemoryService
import io.robothouse.agent.service.DynamicAgentService
import io.robothouse.agent.service.ResponseValidationService
import io.robothouse.agent.service.SkillRouterService

/**
 * Immutable context holding the service dependencies needed by orchestration
 * graph nodes during execution. These values remain constant throughout a
 * single request and are captured in node closures rather than stored in
 * the graph state.
 */
data class OrchestrationGraphContext(
    val skillRouterService: SkillRouterService,
    val dynamicAgentService: DynamicAgentService,
    val conversationMemoryService: ConversationMemoryService,
    val responseValidationService: ResponseValidationService,
    val listener: AgentEventListener
)
