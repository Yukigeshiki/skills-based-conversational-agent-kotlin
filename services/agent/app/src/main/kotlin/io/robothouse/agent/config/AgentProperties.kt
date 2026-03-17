package io.robothouse.agent.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for the agent execution loop.
 *
 * Controls limits on tool executions, timeouts, plan step counts,
 * and whether graph checkpointing is enabled for persistence and
 * audit trails.
 */
@Validated
@ConfigurationProperties(prefix = "agent")
data class AgentProperties(
    @field:Min(1, message = "maxIterations must be at least 1")
    val maxIterations: Int,

    @field:Min(1, message = "toolExecutionTimeoutSeconds must be at least 1")
    val toolExecutionTimeoutSeconds: Long,

    @field:Min(1, message = "maxPlanSteps must be at least 1")
    val maxPlanSteps: Int,

    val checkpointingEnabled: Boolean
)
