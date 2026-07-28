package org.every.nook.api.application.auth

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class AuthErrorCode(override val code: String, override val defaultReason: String, override val type: ErrorType) :
    NookErrorCode {
    INVALID_SOCIAL_CREDENTIAL(
        code = "INVALID_SOCIAL_CREDENTIAL",
        defaultReason = "인증 정보가 유효하지 않습니다.",
        type = ErrorType.UNAUTHORIZED,
    ),
    INVALID_SIGNUP_TOKEN(
        code = "INVALID_SIGNUP_TOKEN",
        defaultReason = "인증 정보가 유효하지 않습니다.",
        type = ErrorType.UNAUTHORIZED,
    ),
    INVALID_REFRESH_TOKEN(
        code = "INVALID_REFRESH_TOKEN",
        defaultReason = "인증 정보가 유효하지 않습니다.",
        type = ErrorType.UNAUTHORIZED,
    ),
    INVALID_ACCESS_TOKEN(
        code = "INVALID_ACCESS_TOKEN",
        defaultReason = "인증 정보가 유효하지 않습니다.",
        type = ErrorType.UNAUTHORIZED,
    ),
}

class InvalidSocialCredentialException : NookException(AuthErrorCode.INVALID_SOCIAL_CREDENTIAL)

class InvalidSignupTokenException : NookException(AuthErrorCode.INVALID_SIGNUP_TOKEN)

class InvalidRefreshTokenException : NookException(AuthErrorCode.INVALID_REFRESH_TOKEN)

class ReusedRefreshTokenException : NookException(AuthErrorCode.INVALID_REFRESH_TOKEN)

class InvalidAccessTokenException : NookException(AuthErrorCode.INVALID_ACCESS_TOKEN)
