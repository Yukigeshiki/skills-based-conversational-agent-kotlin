package io.robothouse.agent.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Exception indicating a requested resource was not found, resulting in a 404 response.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class NotFoundException(
    override val message: String,
    val status: HttpStatus = HttpStatus.NOT_FOUND
) : RuntimeException(message)
