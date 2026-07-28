package org.every.nook.api.application.member

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class MemberErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    DUPLICATE_NICKNAME(
        code = "DUPLICATE_NICKNAME",
        defaultReason = "이미 사용 중인 닉네임입니다.",
        type = ErrorType.CONFLICT,
    ),
    DUPLICATE_SOCIAL_ACCOUNT(
        code = "DUPLICATE_SOCIAL_ACCOUNT",
        defaultReason = "이미 가입된 소셜 계정입니다.",
        type = ErrorType.CONFLICT,
    ),
}

class DuplicateNicknameException(cause: Throwable? = null) :
    NookException(MemberErrorCode.DUPLICATE_NICKNAME, cause = cause)

class DuplicateSocialAccountException(cause: Throwable? = null) :
    NookException(MemberErrorCode.DUPLICATE_SOCIAL_ACCOUNT, cause = cause)
