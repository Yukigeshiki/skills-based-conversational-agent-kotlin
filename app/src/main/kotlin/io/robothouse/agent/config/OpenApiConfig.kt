package io.robothouse.agent.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures OpenAPI/Swagger documentation metadata for the Agent Service API.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(@Value("\${app.version:0.1.0}") version: String): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Agent Service API")
                .description("Agent service API documentation")
                .version(version)
        )
}
