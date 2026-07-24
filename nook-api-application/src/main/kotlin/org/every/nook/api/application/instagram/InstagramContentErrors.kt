package org.every.nook.api.application.instagram

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class InstagramContentErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    INVALID_URL("INSTAGRAM_INVALID_URL", "지원하지 않는 Instagram URL입니다.", ErrorType.INVALID_REQUEST),
    CONTENT_NOT_FOUND("INSTAGRAM_CONTENT_NOT_FOUND", "Instagram 콘텐츠를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    PROVIDER_ERROR("INSTAGRAM_PROVIDER_ERROR", "Instagram 콘텐츠를 가져오지 못했습니다.", ErrorType.BAD_GATEWAY),
    PROVIDER_TIMEOUT("INSTAGRAM_PROVIDER_TIMEOUT", "Instagram 콘텐츠 수집 시간이 초과되었습니다.", ErrorType.GATEWAY_TIMEOUT),
}

class InvalidInstagramUrlException : NookException(InstagramContentErrorCode.INVALID_URL)

class InstagramContentNotFoundException : NookException(InstagramContentErrorCode.CONTENT_NOT_FOUND)

class InstagramProviderException(cause: Throwable? = null) :
    NookException(InstagramContentErrorCode.PROVIDER_ERROR, cause = cause)

class InstagramProviderTimeoutException(cause: Throwable? = null) :
    NookException(InstagramContentErrorCode.PROVIDER_TIMEOUT, cause = cause)
