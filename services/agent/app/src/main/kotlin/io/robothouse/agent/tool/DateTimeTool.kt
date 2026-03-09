package io.robothouse.agent.tool

import dev.langchain4j.agent.tool.Tool
import io.robothouse.agent.util.log
import org.springframework.stereotype.Component
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * LangChain4j tool that provides current date and time for a given IANA timezone.
 */
@Component
class DateTimeTool {

    @Tool(
        "Returns the current date and time for a given timezone. " +
        "The timezone parameter should be a valid IANA timezone ID " +
        "such as 'America/New_York', 'Europe/London', or 'Asia/Tokyo'."
    )
    fun getCurrentDateTime(timezone: String): String {
        log.debug { "Getting current date/time for timezone: $timezone" }
        val zoneId = try {
            ZoneId.of(timezone)
        } catch (_: DateTimeException) {
            return "Invalid timezone: '$timezone'. Please use a valid IANA timezone ID " +
                "such as 'America/New_York', 'Europe/London', or 'Asia/Tokyo'."
        }
        val now = ZonedDateTime.now(zoneId)
        val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        log.debug { "Current date/time in $timezone: $formatted" }
        return formatted
    }
}
