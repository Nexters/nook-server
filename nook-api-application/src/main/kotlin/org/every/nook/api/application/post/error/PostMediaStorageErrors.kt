package org.every.nook.api.application.post.error

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class PostMediaStorageErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    STORAGE_FAILED(
        code = "POST_MEDIA_STORAGE_FAILED",
        defaultReason = "게시물 미디어를 저장하지 못했습니다.",
        type = ErrorType.BAD_GATEWAY,
    ),
    STORAGE_TIMEOUT(
        code = "POST_MEDIA_STORAGE_TIMEOUT",
        defaultReason = "게시물 미디어 저장 시간이 초과되었습니다.",
        type = ErrorType.GATEWAY_TIMEOUT,
    ),
}

class PostMediaStorageException(cause: Throwable? = null) :
    NookException(PostMediaStorageErrorCode.STORAGE_FAILED, cause = cause)

class PostMediaStorageTimeoutException(cause: Throwable? = null) :
    NookException(PostMediaStorageErrorCode.STORAGE_TIMEOUT, cause = cause)
