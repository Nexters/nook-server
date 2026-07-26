package org.every.nook.api.application.content

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class PostContentErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    UNSUPPORTED_URL("UNSUPPORTED_POST_URL", "지원하지 않는 게시물 URL입니다.", ErrorType.INVALID_REQUEST),
    CONTENT_NOT_FOUND("POST_CONTENT_NOT_FOUND", "게시물 콘텐츠를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    PROVIDER_ERROR("POST_CONTENT_PROVIDER_ERROR", "게시물 콘텐츠를 가져오지 못했습니다.", ErrorType.BAD_GATEWAY),
    PROVIDER_TIMEOUT("POST_CONTENT_PROVIDER_TIMEOUT", "게시물 콘텐츠 수집 시간이 초과되었습니다.", ErrorType.GATEWAY_TIMEOUT),
}

class UnsupportedPostUrlException : NookException(PostContentErrorCode.UNSUPPORTED_URL)

class PostContentNotFoundException : NookException(PostContentErrorCode.CONTENT_NOT_FOUND)

class PostContentProviderException(cause: Throwable? = null) :
    NookException(PostContentErrorCode.PROVIDER_ERROR, cause = cause)

class PostContentProviderTimeoutException(cause: Throwable? = null) :
    NookException(PostContentErrorCode.PROVIDER_TIMEOUT, cause = cause)
