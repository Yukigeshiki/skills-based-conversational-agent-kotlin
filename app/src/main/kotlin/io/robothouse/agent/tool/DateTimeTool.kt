package io.robothouse.agent.tool

import dev.langchain4j.agent.tool.Tool
import io.robothouse.agent.util.log
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
class DateTimeTool {

    @Tool("Returns the current date and time for a given timezone. The timezone parameter should be a valid IANA timezone ID such as 'America/New_York', 'Europe/London', or 'Asia/Tokyo'.")
    fun getCurrentDateTime(timezone: String): String {
        log.debug { "Getting current date/time for timezone: $timezone" }
        val zoneId = ZoneId.of(timezone)
        val now = ZonedDateTime.now(zoneId)
        val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        log.debug { "Current date/time in $timezone: $formatted" }
        return formatted
    }
}
