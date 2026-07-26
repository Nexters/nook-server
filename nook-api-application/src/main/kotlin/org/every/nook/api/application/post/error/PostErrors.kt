package org.every.nook.api.application.post.error

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class PostErrorCode(override val code: String, override val defaultReason: String, override val type: ErrorType) :
    NookErrorCode {
    POST_NOT_FOUND(
        code = "POST_NOT_FOUND",
        defaultReason = "게시물을 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
}

class PostNotFoundException : NookException(PostErrorCode.POST_NOT_FOUND)
