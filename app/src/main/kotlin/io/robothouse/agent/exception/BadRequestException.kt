package io.robothouse.agent.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Exception indicating a client error resulting in a 400 Bad Request response.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class BadRequestException(
    override val message: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST
) : RuntimeException(message)
