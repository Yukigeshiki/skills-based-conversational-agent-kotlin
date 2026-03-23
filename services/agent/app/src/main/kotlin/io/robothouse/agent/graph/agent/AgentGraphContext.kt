package io.robothouse.agent.graph.agent

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.TaskMemory
import io.robothouse.agent.model.ToolExecutionStep
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver
import org.bsc.langgraph4j.serializer.StateSerializer

typealias EmitEventFn = (AgentEventListener, () -> AgentEvent) -> Unit
typealias ExecuteToolFn = (ToolExecutionRequest, Map<String, ToolExecutor>, Int, AgentEventListener) -> ToolExecutionStep
typealias CheckTimeoutFn = (Long, Long) -> Unit

/**
 * Immutable context holding the infrastructure dependencies needed by
 * graph nodes during execution. These values remain constant throughout
 * a single agent loop invocation and are captured in node closures
 * rather than stored in the graph state.
 */
data class AgentGraphContext(
    val agentChatModel: ChatModel,
    val agentStreamingChatModel: StreamingChatModel? = null,
    val systemPrompt: String,
    val skillName: String,
    val specifications: List<ToolSpecification>,
    val executors: Map<String, ToolExecutor>,
    val listener: AgentEventListener,
    val emitFinalResponse: Boolean,
    val taskMemory: TaskMemory,
    val maxIterations: Int,
    val startTime: Long,
    val timeoutMillis: Long,
    val emitEvent: EmitEventFn,
    val executeTool: ExecuteToolFn,
    val checkTimeout: CheckTimeoutFn,
    val stateSerializer: StateSerializer<AgentGraphState>? = null,
    val checkpointSaver: BaseCheckpointSaver? = null,
    val conversationId: String? = null,
    val requiresApproval: Boolean = false
)
