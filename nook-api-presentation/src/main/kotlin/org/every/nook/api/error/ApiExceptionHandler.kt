package org.every.nook.api.error

import org.every.nook.api.application.auth.InvalidRefreshTokenException
import org.every.nook.api.application.auth.InvalidSignupTokenException
import org.every.nook.api.application.auth.InvalidSocialCredentialException
import org.every.nook.api.application.auth.ReusedRefreshTokenException
import org.every.nook.api.application.member.DuplicateNicknameException
import org.every.nook.api.application.member.DuplicateSocialAccountException
import org.every.nook.api.application.member.MemberNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(InvalidSocialCredentialException::class)
    fun invalidSocialCredential(): ResponseEntity<ApiErrorResponse> = unauthorized("INVALID_SOCIAL_CREDENTIAL")

    @ExceptionHandler(InvalidSignupTokenException::class)
    fun invalidSignupToken(): ResponseEntity<ApiErrorResponse> = unauthorized("INVALID_SIGNUP_TOKEN")

    @ExceptionHandler(
        InvalidRefreshTokenException::class,
        ReusedRefreshTokenException::class,
        MemberNotFoundException::class,
    )
    fun invalidRefreshToken(): ResponseEntity<ApiErrorResponse> = unauthorized("INVALID_REFRESH_TOKEN")

    @ExceptionHandler(DuplicateNicknameException::class)
    fun duplicateNickname(): ResponseEntity<ApiErrorResponse> = conflict(
        code = "DUPLICATE_NICKNAME",
        message = "이미 사용 중인 닉네임입니다.",
    )

    @ExceptionHandler(DuplicateSocialAccountException::class)
    fun duplicateSocialAccount(): ResponseEntity<ApiErrorResponse> = conflict(
        code = "DUPLICATE_SOCIAL_ACCOUNT",
        message = "이미 가입된 소셜 계정입니다.",
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse(
                code = "INVALID_REQUEST",
                message = "요청값이 올바르지 않습니다.",
                fieldErrors = exception.bindingResult.fieldErrors.map {
                    FieldErrorResponse(it.field, it.defaultMessage ?: "Invalid value")
                },
            ),
        )

    @ExceptionHandler(
        IllegalArgumentException::class,
        MissingRequestHeaderException::class,
        HttpMessageNotReadableException::class,
    )
    fun invalidRequest(): ResponseEntity<ApiErrorResponse> = ResponseEntity.badRequest().body(
        ApiErrorResponse("INVALID_REQUEST", "요청값이 올바르지 않습니다."),
    )

    private fun unauthorized(code: String): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(code, "인증 정보가 유효하지 않습니다."),
        )

    private fun conflict(code: String, message: String): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse(code, message))
}

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
)

data class FieldErrorResponse(val field: String, val reason: String)
