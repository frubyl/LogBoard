package com.github.logboard.core.exception

import com.github.logboard.core.dto.ErrorResponse
import com.github.logboard.core.exception.authentication.UnauthorizedException
import com.github.logboard.core.exception.common.AlreadyExistsException
import com.github.logboard.core.exception.common.ForbiddenException
import com.github.logboard.core.exception.common.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice
class ExceptionControllerAdvice : ResponseEntityExceptionHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(ExceptionControllerAdvice::class.java)
    }

    @ExceptionHandler(
        NotFoundException::class,
        AlreadyExistsException::class,
        UnauthorizedException::class,
        ForbiddenException::class,
        BadCredentialsException::class,
        IllegalArgumentException::class
    )
    fun handleApplicationExceptions(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val status = when (ex) {
            is NotFoundException -> HttpStatus.NOT_FOUND
            is AlreadyExistsException -> HttpStatus.CONFLICT
            is UnauthorizedException, is BadCredentialsException -> HttpStatus.UNAUTHORIZED
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is IllegalArgumentException -> HttpStatus.BAD_REQUEST
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        log.error("Application exception occurred: ${ex.javaClass.simpleName} - ${ex.message}", ex)
        return handleExceptionInternal(ex, buildErrorResponse(status, ex), HttpHeaders(), status, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<Any>? {
        log.error("Unhandled exception occurred: ${ex.javaClass.simpleName} - ${ex.message}", ex)
        return handleExceptionInternal(
            ex,
            buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex),
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
        log.warn("Validation failed: ${ex.fieldErrors.size} field errors")
       val error = ErrorResponse(
           code = HttpStatus.BAD_REQUEST.name,
           message = ex.message ?: "Validation failed"
       )
        return handleExceptionInternal(ex, error, headers, status, request)
    }

    private fun buildErrorResponse(
        status: HttpStatus,
        exception: Exception
    ): ErrorResponse {
        val message = exception.message ?: "No message"
        val error = ErrorResponse(
            code = status.name,
            message = message
        )
        return error
    }
}
