package org.every.nook.api.presentation.error

import jakarta.validation.ConstraintViolationException
import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookException
import org.every.nook.api.presentation.response.ApiError
import org.every.nook.api.presentation.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NookException::class)
    fun handleNookException(exception: NookException): ResponseEntity<ApiResponse<Nothing>> = failureResponse(
        status = exception.errorCode.type.toHttpStatus(),
        errorCode = exception.errorCode.code,
        reason = exception.reason,
        data = exception.data,
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val violations = exception.bindingResult.fieldErrors
            .map { error ->
                FieldViolation(
                    field = error.field,
                    reason = error.defaultMessage ?: INVALID_FIELD_REASON,
                )
            }
            .sortedBy(FieldViolation::field)

        return invalidRequestResponse(violations)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ApiResponse<Nothing>> {
        val violations = exception.constraintViolations
            .map { violation ->
                FieldViolation(
                    field = violation.propertyPath.toString(),
                    reason = violation.message,
                )
            }
            .sortedBy(FieldViolation::field)

        return invalidRequestResponse(violations)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HandlerMethodValidationException::class,
    )
    fun handleInvalidRequest(): ResponseEntity<ApiResponse<Nothing>> = failureResponse(
        status = HttpStatus.BAD_REQUEST,
        errorCode = INVALID_REQUEST_ERROR_CODE,
        reason = INVALID_REQUEST_ERROR_REASON,
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Unexpected API exception", exception)

        return failureResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode = INTERNAL_SERVER_ERROR_CODE,
            reason = INTERNAL_SERVER_ERROR_REASON,
        )
    }

    private fun invalidRequestResponse(violations: List<FieldViolation>): ResponseEntity<ApiResponse<Nothing>> =
        failureResponse(
            status = HttpStatus.BAD_REQUEST,
            errorCode = INVALID_REQUEST_ERROR_CODE,
            reason = INVALID_REQUEST_ERROR_REASON,
            data = mapOf(VIOLATIONS_KEY to violations),
        )

    private fun failureResponse(
        status: HttpStatus,
        errorCode: String,
        reason: String,
        data: Map<String, Any?>? = null,
    ): ResponseEntity<ApiResponse<Nothing>> = ResponseEntity
        .status(status)
        .body(
            ApiResponse.fail(
                ApiError(
                    errorCode = errorCode,
                    reason = reason,
                    data = data,
                ),
            ),
        )

    private fun ErrorType.toHttpStatus(): HttpStatus = when (this) {
        ErrorType.INVALID_REQUEST -> HttpStatus.BAD_REQUEST
        ErrorType.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        ErrorType.FORBIDDEN -> HttpStatus.FORBIDDEN
        ErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorType.CONFLICT -> HttpStatus.CONFLICT
        ErrorType.INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
    }

    private companion object {
        val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

        const val INVALID_REQUEST_ERROR_CODE = "INVALID_REQUEST"
        const val INVALID_REQUEST_ERROR_REASON = "요청 값이 올바르지 않습니다."
        const val INTERNAL_SERVER_ERROR_CODE = "INTERNAL_SERVER_ERROR"
        const val INTERNAL_SERVER_ERROR_REASON = "서버 오류가 발생했습니다."
        const val INVALID_FIELD_REASON = "올바르지 않은 값입니다."
        const val VIOLATIONS_KEY = "violations"
    }
}
