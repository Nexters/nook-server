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
    SHARE_LINK_NOT_FOUND("SHARE_LINK_NOT_FOUND", "공유 링크를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    SHARE_LINK_REVOKED("SHARE_LINK_REVOKED", "공유가 해제된 링크입니다.", ErrorType.GONE),
    SHARE_LINK_EXPIRED("SHARE_LINK_EXPIRED", "공유 기간이 만료된 링크입니다.", ErrorType.GONE),
    SHARED_GROUP_UNAVAILABLE("SHARED_GROUP_UNAVAILABLE", "공유 그룹을 더 이상 볼 수 없습니다.", ErrorType.GONE),
    SHARED_RESOURCE_NOT_FOUND("SHARED_RESOURCE_NOT_FOUND", "공유 범위에서 대상을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
}

class GroupNotFoundException : NookException(GroupErrorCode.GROUP_NOT_FOUND)

class InvalidGroupException(cause: IllegalArgumentException) :
    NookException(errorCode = GroupErrorCode.INVALID_GROUP, cause = cause)

class ShareLinkNotFoundException : NookException(GroupErrorCode.SHARE_LINK_NOT_FOUND)
class ShareLinkRevokedException : NookException(GroupErrorCode.SHARE_LINK_REVOKED)
class ShareLinkExpiredException : NookException(GroupErrorCode.SHARE_LINK_EXPIRED)
class SharedGroupUnavailableException : NookException(GroupErrorCode.SHARED_GROUP_UNAVAILABLE)
class SharedResourceNotFoundException : NookException(GroupErrorCode.SHARED_RESOURCE_NOT_FOUND)
