package org.every.nook.api.application.place.error

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class PlaceErrorCode(override val code: String, override val defaultReason: String, override val type: ErrorType) :
    NookErrorCode {
    PLACE_NOT_FOUND(
        code = "PLACE_NOT_FOUND",
        defaultReason = "장소를 찾을 수 없습니다.",
        type = ErrorType.NOT_FOUND,
    ),
}

class PlaceNotFoundException : NookException(PlaceErrorCode.PLACE_NOT_FOUND)
