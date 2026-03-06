package io.robothouse.agent

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ApplicationTest {

    @Test
    fun `application context loads`() {
        // Context loads if we get here
    }
}
