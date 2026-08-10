package org.every.nook.api.presentation.place.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.MapPlaceView
import org.every.nook.api.application.place.RecentPlaceSliceView
import org.every.nook.api.application.place.RecentPlaceView
import org.every.nook.api.presentation.place.RecentPlaceCursorCodec
import java.math.BigDecimal

data class MapPlaceResponse(
    @field:Schema(description = "장소 식별자")
    val id: Long,
    @field:Schema(description = "장소명")
    val name: String,
    @field:Schema(description = "장소 지역", nullable = true, example = "서울")
    val city: String?,
    @field:Schema(description = "장소 위도")
    val latitude: BigDecimal,
    @field:Schema(description = "장소 경도")
    val longitude: BigDecimal,
    @field:Schema(description = "대표 그룹 색상 코드", example = "YELLOW")
    val color: String,
    @field:Schema(description = "장소 대표 썸네일 URL", nullable = true)
    val thumbnailUrl: String?,
    @field:Schema(description = "장소 대표 태그 목록(최대 4개)")
    val tags: List<String>,
) {
    companion object {
        fun from(view: MapPlaceView): MapPlaceResponse = MapPlaceResponse(
            id = view.id,
            name = view.name,
            city = view.city,
            latitude = view.latitude,
            longitude = view.longitude,
            color = view.color,
            thumbnailUrl = view.thumbnailUrl,
            tags = view.tags,
        )
    }
}

data class RecentPlaceSliceResponse(
    @field:Schema(description = "최근 저장 공간 목록")
    val items: List<RecentPlaceResponse>,
    @field:Schema(description = "다음 목록 조회 cursor", nullable = true)
    val nextCursor: String?,
    @field:Schema(description = "다음 목록 존재 여부")
    val hasNext: Boolean,
) {
    companion object {
        fun from(view: RecentPlaceSliceView): RecentPlaceSliceResponse = RecentPlaceSliceResponse(
            items = view.items.map(RecentPlaceResponse::from),
            nextCursor = view.nextCursor?.let(RecentPlaceCursorCodec::encode),
            hasNext = view.hasNext,
        )
    }
}

data class RecentPlaceResponse(
    @field:Schema(description = "장소 식별자")
    val id: Long,
    @field:Schema(description = "장소명")
    val name: String,
    @field:Schema(description = "장소 지역", nullable = true, example = "서울")
    val city: String?,
    @field:Schema(description = "장소 주소")
    val address: String,
    @field:Schema(description = "장소 카테고리", nullable = true)
    val category: String?,
    @field:Schema(description = "장소 위도")
    val latitude: BigDecimal,
    @field:Schema(description = "장소 경도")
    val longitude: BigDecimal,
    @field:Schema(description = "장소 대표 썸네일 URL", nullable = true)
    val thumbnailUrl: String?,
    @field:Schema(description = "장소 대표 태그 목록(최대 4개)")
    val tags: List<String>,
) {
    companion object {
        fun from(view: RecentPlaceView): RecentPlaceResponse = RecentPlaceResponse(
            id = view.id,
            name = view.name,
            city = view.city,
            address = view.address,
            category = view.category,
            latitude = view.latitude,
            longitude = view.longitude,
            thumbnailUrl = view.thumbnailUrl,
            tags = view.tags,
        )
    }
}
