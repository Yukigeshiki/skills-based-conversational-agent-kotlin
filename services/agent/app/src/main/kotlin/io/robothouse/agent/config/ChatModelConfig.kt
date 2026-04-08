package io.robothouse.agent.config

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import io.robothouse.agent.util.RetryingChatModel
import io.robothouse.agent.util.RetryingStreamingChatModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties(prefix = "agent.models")
data class ChatModelProperties(
    val apiKey: String,
    val logRequests: Boolean,
    val logResponses: Boolean,
    val timeoutSeconds: Long,
    val agent: ModelSettings,
    val light: ModelSettings
) {
    data class ModelSettings(
        val modelName: String,
        val temperature: Double,
        val maxTokens: Int
    )

    override fun toString(): String =
        "ChatModelProperties(apiKey=***, logRequests=$logRequests, logResponses=$logResponses, " +
            "timeoutSeconds=$timeoutSeconds, agent=$agent, light=$light)"
}

@Configuration
@EnableConfigurationProperties(
    ChatModelProperties::class,
    AgentProperties::class,
    LlmRetryProperties::class
)
class ChatModelConfig {

    @Bean
    @Qualifier("agentChatModel")
    fun agentChatModel(
        properties: ChatModelProperties,
        retryProperties: LlmRetryProperties
    ): ChatModel = RetryingChatModel(
        delegate = buildModel(properties, properties.agent),
        properties = retryProperties
    )

    @Bean
    @Qualifier("agentStreamingChatModel")
    fun agentStreamingChatModel(
        properties: ChatModelProperties,
        retryProperties: LlmRetryProperties
    ): StreamingChatModel =
        RetryingStreamingChatModel(buildStreamingModel(properties, properties.agent), retryProperties)

    @Bean
    @Qualifier("lightChatModel")
    fun lightChatModel(properties: ChatModelProperties): ChatModel =
        // Intentionally NOT wrapped in RetryingChatModel: the auxiliary services
        // that use this bean already have fail-fast fallbacks, and retrying behind
        // them would silently delay the chat flow. Retry is reserved for the main
        // agent loop, which installs a budget supplier via LlmRetryEventEmitter.
        buildModel(properties, properties.light)

    private fun buildModel(properties: ChatModelProperties, settings: ChatModelProperties.ModelSettings): ChatModel =
        AnthropicChatModel.builder()
            .apiKey(properties.apiKey)
            .modelName(settings.modelName)
            .temperature(settings.temperature)
            .maxTokens(settings.maxTokens)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds))
            // Disable LangChain4j's built-in retry — RetryingChatModel handles it with
            // jittered exponential backoff and explicit classification
            .maxRetries(0)
            .logRequests(properties.logRequests)
            .logResponses(properties.logResponses)
            .build()

    private fun buildStreamingModel(properties: ChatModelProperties, settings: ChatModelProperties.ModelSettings): StreamingChatModel =
        AnthropicStreamingChatModel.builder()
            .apiKey(properties.apiKey)
            .modelName(settings.modelName)
            .temperature(settings.temperature)
            .maxTokens(settings.maxTokens)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds))
            .logRequests(properties.logRequests)
            .logResponses(properties.logResponses)
            .build()
}
