package io.robothouse.agent.config

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.chat.ChatModel
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
@EnableConfigurationProperties(ChatModelProperties::class, AgentProperties::class)
class ChatModelConfig {

    @Bean
    @Qualifier("agentChatModel")
    fun agentChatModel(properties: ChatModelProperties): ChatModel =
        buildModel(properties, properties.agent)

    @Bean
    @Qualifier("lightChatModel")
    fun lightChatModel(properties: ChatModelProperties): ChatModel =
        buildModel(properties, properties.light)

    private fun buildModel(properties: ChatModelProperties, settings: ChatModelProperties.ModelSettings): ChatModel =
        AnthropicChatModel.builder()
            .apiKey(properties.apiKey)
            .modelName(settings.modelName)
            .temperature(settings.temperature)
            .maxTokens(settings.maxTokens)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds))
            .logRequests(properties.logRequests)
            .logResponses(properties.logResponses)
            .build()
}
