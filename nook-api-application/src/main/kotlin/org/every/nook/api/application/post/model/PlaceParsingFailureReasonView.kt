package org.every.nook.api.application.post.model

import org.every.nook.api.domain.place.PlaceParsingStatus

object PlaceParsingFailureReasonView {
    const val PLACE_NOT_FOUND = "게시물에서 위치 정보를 찾지 못했어요"

    fun from(status: PlaceParsingStatus): String? = PLACE_NOT_FOUND.takeIf { status == PlaceParsingStatus.FAILED }
}
