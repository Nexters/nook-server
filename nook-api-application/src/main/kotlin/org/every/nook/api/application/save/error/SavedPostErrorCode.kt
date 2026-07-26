package org.every.nook.api.application.save.error

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode

enum class SavedPostErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    INVALID_INSTAGRAM_POST_URL(
        code = "INVALID_INSTAGRAM_POST_URL",
        defaultReason = "지원하지 않는 Instagram 게시물 URL입니다.",
        type = ErrorType.INVALID_REQUEST,
    ),
    SAVED_POST_NOT_FOUND(
        code = "SAVED_POST_NOT_FOUND",
        defaultReason = "저장한 게시물을 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
}
