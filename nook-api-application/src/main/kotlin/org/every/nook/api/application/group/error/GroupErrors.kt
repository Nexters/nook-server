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
    GROUP_NAME_DUPLICATED(
        code = "GROUP_NAME_DUPLICATED",
        defaultReason = "같은 이름의 그룹이 이미 존재합니다.",
        type = ErrorType.CONFLICT,
    ),
    INVALID_GROUP(
        code = "INVALID_GROUP",
        defaultReason = "그룹 값이 올바르지 않습니다.",
        type = ErrorType.INVALID_REQUEST,
    ),
}

class GroupNotFoundException : NookException(GroupErrorCode.GROUP_NOT_FOUND)

class GroupNameDuplicatedException : NookException(GroupErrorCode.GROUP_NAME_DUPLICATED)

class InvalidGroupException(cause: IllegalArgumentException) :
    NookException(errorCode = GroupErrorCode.INVALID_GROUP, cause = cause)
