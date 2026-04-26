package com.github.logboard.log.exception

import com.github.logboard.log.dto.ErrorResponse
import com.github.logboard.log.exception.common.ForbiddenException
import com.github.logboard.log.exception.common.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(NotFoundException::class, ForbiddenException::class, IllegalArgumentException::class)
    fun handleApplicationExceptions(ex: Exception, request: WebRequest): ResponseEntity<Any>? {
        val status = when (ex) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is IllegalArgumentException -> HttpStatus.BAD_REQUEST
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        log.error("Application exception: ${ex.javaClass.simpleName} - ${ex.message}")
        return handleExceptionInternal(ex, buildError(status, ex), HttpHeaders(), status, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: WebRequest): ResponseEntity<Any>? {
        log.error("Unhandled exception: ${ex.javaClass.simpleName} - ${ex.message}", ex)
        return handleExceptionInternal(
            ex,
            buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex),
            HttpHeaders(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            request
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val error = ErrorResponse(code = HttpStatus.BAD_REQUEST.name, message = ex.message ?: "Validation failed")
        return handleExceptionInternal(ex, error, headers, status, request)
    }

    private fun buildError(status: HttpStatus, ex: Exception) =
        ErrorResponse(code = status.name, message = ex.message ?: "No message")
}
