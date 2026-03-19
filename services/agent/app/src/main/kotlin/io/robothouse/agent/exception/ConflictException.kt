package io.robothouse.agent.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Exception indicating a resource conflict, resulting in a 409 response.
 */
@ResponseStatus(HttpStatus.CONFLICT)
class ConflictException(
    override val message: String,
    val status: HttpStatus = HttpStatus.CONFLICT
) : RuntimeException(message)
