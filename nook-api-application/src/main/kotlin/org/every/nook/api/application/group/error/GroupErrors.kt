package org.every.nook.api.application.group.error

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class GroupErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    GROUP_NOT_FOUND(
        code = "GROUP_NOT_FOUND",
        defaultReason = "그룹을 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
    INVALID_GROUP(
        code = "INVALID_GROUP",
        defaultReason = "그룹 값이 올바르지 않습니다.",
        type = ErrorType.INVALID_REQUEST,
    ),
}

class GroupNotFoundException : NookException(GroupErrorCode.GROUP_NOT_FOUND)

class InvalidGroupException(cause: IllegalArgumentException) :
    NookException(errorCode = GroupErrorCode.INVALID_GROUP, cause = cause)
