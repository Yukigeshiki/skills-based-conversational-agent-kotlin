package io.robothouse.agent.config

import io.robothouse.agent.exception.BadRequestException
import io.robothouse.agent.exception.InternalServerErrorException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.util.log
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.NoHandlerFoundException

/**
 * Global exception handler that maps application exceptions to RFC 9457
 * ProblemDetail responses with appropriate HTTP status codes.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequestException(e: BadRequestException): ResponseEntity<ProblemDetail> {
        return buildResponse(e.message, e.status)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(e: NotFoundException): ResponseEntity<ProblemDetail> {
        return buildResponse(e.message, e.status)
    }

    @ExceptionHandler(InternalServerErrorException::class)
    fun handleInternalServerErrorException(e: InternalServerErrorException): ResponseEntity<ProblemDetail> {
        log.error(e) { "Internal server error" }
        return buildResponse("A problem has occurred while processing your request", HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> {
        log.warn(e) { "HTTP message not readable — root cause: ${e.rootCause?.message}" }
        return buildResponse("Invalid request format or data", HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: e.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "Validation failed"
        log.warn { "Validation failed: $message" }
        return buildResponse(message, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(e: ConstraintViolationException): ResponseEntity<ProblemDetail> {
        val message = e.constraintViolations.firstOrNull()?.message
            ?: "Validation failed"
        log.warn { "Constraint violation: $message" }
        return buildResponse(message, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(e: NoHandlerFoundException): ResponseEntity<ProblemDetail> {
        log.warn { "No handler found: ${e.requestURL}" }
        return buildResponse("The requested endpoint was not found", HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(Throwable::class)
    fun handleGeneralException(e: Throwable): ResponseEntity<ProblemDetail> {
        log.error(e) { "Unexpected error" }
        return buildResponse("A problem has occurred while processing your request", HttpStatus.INTERNAL_SERVER_ERROR)
    }

    companion object {
        /**
         * Builds a [ProblemDetail] response with the given detail message and HTTP status.
         */
        private fun buildResponse(detail: String?, status: HttpStatus): ResponseEntity<ProblemDetail> {
            val problem = ProblemDetail.forStatusAndDetail(status, detail ?: "No detail provided")
            problem.title = status.reasonPhrase
            return ResponseEntity(problem, status)
        }
    }
}
