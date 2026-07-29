package org.every.nook.api.application.place

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException

enum class PlaceSearchErrorCode(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    INVALID_REQUEST("PLACE_SEARCH_INVALID_REQUEST", "장소 검색 조건이 올바르지 않습니다.", ErrorType.INVALID_REQUEST),
    PROVIDER_ERROR("PLACE_SEARCH_PROVIDER_ERROR", "장소 후보를 검색하지 못했습니다.", ErrorType.BAD_GATEWAY),
    PROVIDER_TIMEOUT("PLACE_SEARCH_PROVIDER_TIMEOUT", "장소 후보 검색 시간이 초과되었습니다.", ErrorType.GATEWAY_TIMEOUT),
    INVALID_SELECTION("PLACE_SELECTION_INVALID", "장소 선택 정보가 올바르지 않습니다.", ErrorType.INVALID_REQUEST),
    PARSING_IN_PROGRESS(
        "PLACE_PARSING_IN_PROGRESS",
        "장소 분석이 진행 중입니다.",
        ErrorType.CONFLICT,
    ),
}

class InvalidPlaceSearchRequestException : NookException(PlaceSearchErrorCode.INVALID_REQUEST)

class PlaceSearchProviderException(cause: Throwable? = null) :
    NookException(PlaceSearchErrorCode.PROVIDER_ERROR, cause = cause)

class PlaceSearchProviderTimeoutException(cause: Throwable? = null) :
    NookException(PlaceSearchErrorCode.PROVIDER_TIMEOUT, cause = cause)

class InvalidPlaceSelectionException : NookException(PlaceSearchErrorCode.INVALID_SELECTION)

class PlaceParsingInProgressException : NookException(PlaceSearchErrorCode.PARSING_IN_PROGRESS)
