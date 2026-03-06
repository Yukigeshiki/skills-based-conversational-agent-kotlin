package io.robothouse.agent

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

private val log = KotlinLogging.logger {}

/**
 * Spring Boot application entry point for the Agent Service.
 */
@Validated
@SpringBootApplication
class Application

/**
 * Logs application version and active profiles on startup.
 */
@Component
class StartupLogger(private val environment: Environment) {

    @EventListener(ApplicationReadyEvent::class)
    fun logStartupInfo() {
        val version = javaClass.`package`?.implementationVersion ?: "development"
        val profiles = environment.activeProfiles.takeIf { it.isNotEmpty() }?.joinToString() ?: "default"

        log.info { "Agent Service started successfully - Version: $version, Profiles: $profiles" }
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
