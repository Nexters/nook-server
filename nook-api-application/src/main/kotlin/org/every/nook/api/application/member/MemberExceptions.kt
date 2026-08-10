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
    MEMBER_NOT_FOUND(
        code = "MEMBER_NOT_FOUND",
        defaultReason = "회원을 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
    PROFILE_IMAGE_UPLOAD_UNAVAILABLE(
        code = "PROFILE_IMAGE_UPLOAD_UNAVAILABLE",
        defaultReason = "프로필 이미지 업로드 URL을 발급할 수 없습니다.",
        type = ErrorType.BAD_GATEWAY,
    ),
}

class DuplicateNicknameException(cause: Throwable? = null) :
    NookException(MemberErrorCode.DUPLICATE_NICKNAME, cause = cause)

class DuplicateSocialAccountException(cause: Throwable? = null) :
    NookException(MemberErrorCode.DUPLICATE_SOCIAL_ACCOUNT, cause = cause)

class MemberNotFoundException : NookException(MemberErrorCode.MEMBER_NOT_FOUND)

class ProfileImageUploadUnavailableException(cause: Throwable? = null) :
    NookException(MemberErrorCode.PROFILE_IMAGE_UPLOAD_UNAVAILABLE, cause = cause)
