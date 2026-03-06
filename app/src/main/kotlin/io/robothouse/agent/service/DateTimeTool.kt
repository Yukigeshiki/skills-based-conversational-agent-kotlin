package io.robothouse.agent.service

import dev.langchain4j.agent.tool.Tool
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Component
class DateTimeTool {

    @Tool("Returns the current date and time for a given timezone. The timezone parameter should be a valid IANA timezone ID such as 'America/New_York', 'Europe/London', or 'Asia/Tokyo'.")
    fun getCurrentDateTime(timezone: String): String {
        logger.debug { "Getting current date/time for timezone: $timezone" }
        val zoneId = ZoneId.of(timezone)
        val now = ZonedDateTime.now(zoneId)
        val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        logger.debug { "Current date/time in $timezone: $formatted" }
        return formatted
    }
}
