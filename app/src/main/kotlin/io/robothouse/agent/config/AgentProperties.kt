package io.robothouse.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent")
data class AgentProperties(
    val maxToolExecutions: Int,
    val toolExecutionTimeoutSeconds: Long,
    val maxPlanSteps: Int
)
