package io.robothouse.agent

import io.robothouse.agent.integration.RedisContainerConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(RedisContainerConfig::class)
class ApplicationTest {

    @Test
    fun `application context loads`() {
        // Context loads if we get here
    }
}
