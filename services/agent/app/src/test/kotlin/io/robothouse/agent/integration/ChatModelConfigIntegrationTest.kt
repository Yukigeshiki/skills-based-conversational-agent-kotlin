package io.robothouse.agent.integration

import dev.langchain4j.model.chat.ChatModel
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@Import(PostgresContainerConfig::class, RedisContainerConfig::class)
class ChatModelConfigIntegrationTest {

    @Autowired
    @Qualifier("agentChatModel")
    lateinit var agentChatModel: ChatModel

    @Autowired
    @Qualifier("lightChatModel")
    lateinit var lightChatModel: ChatModel

    @Test
    fun `agentChatModel bean is available`() {
        assertNotNull(agentChatModel)
    }

    @Test
    fun `lightChatModel bean is available`() {
        assertNotNull(lightChatModel)
    }

    @Test
    fun `agent and light chat models are distinct beans`() {
        assertNotSame(agentChatModel, lightChatModel)
    }
}
