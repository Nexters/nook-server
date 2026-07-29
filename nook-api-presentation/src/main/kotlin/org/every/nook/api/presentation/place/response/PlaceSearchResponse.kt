package org.every.nook.api.presentation.place.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.PlaceSearchResultView
import org.every.nook.api.application.place.PlaceSearchSliceView
import java.math.BigDecimal

data class PlaceSearchSliceResponse(
    @field:Schema(description = "장소 검색 결과")
    val items: List<PlaceSearchResponse>,
    @field:Schema(description = "현재 페이지 번호. 0부터 시작")
    val page: Int,
    @field:Schema(description = "페이지당 장소 수")
    val size: Int,
    @field:Schema(description = "다음 페이지 존재 여부")
    val hasNext: Boolean,
) {
    companion object {
        fun from(view: PlaceSearchSliceView): PlaceSearchSliceResponse = PlaceSearchSliceResponse(
            items = view.items.map(PlaceSearchResponse::from),
            page = view.page,
            size = view.size,
            hasNext = view.hasNext,
        )
    }
}

data class PlaceSearchResponse(
    @field:Schema(description = "게시물 연결 시 사용할 만료형 장소 선택 토큰")
    val selectionToken: String,
    @field:Schema(description = "장소명")
    val name: String,
    @field:Schema(description = "장소 주소")
    val address: String,
    @field:Schema(description = "장소 카테고리", nullable = true)
    val category: String?,
    @field:Schema(description = "장소 위도")
    val latitude: BigDecimal,
    @field:Schema(description = "장소 경도")
    val longitude: BigDecimal,
    @field:Schema(description = "검색 기준 좌표와의 거리(m)", nullable = true)
    val distanceMeters: Int?,
) {
    companion object {
        fun from(view: PlaceSearchResultView): PlaceSearchResponse = PlaceSearchResponse(
            selectionToken = view.selectionToken,
            name = view.candidate.name,
            address = view.candidate.address,
            category = view.candidate.category,
            latitude = view.candidate.latitude,
            longitude = view.candidate.longitude,
            distanceMeters = view.candidate.distanceMeters,
        )
    }
}
