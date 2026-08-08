package org.every.nook.api.presentation.place.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.StoredPlaceSearchSliceView
import org.every.nook.api.application.place.StoredPlaceSearchView
import java.math.BigDecimal

data class StoredPlaceSearchSliceResponse(
    val items: List<StoredPlaceSearchResponse>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(view: StoredPlaceSearchSliceView): StoredPlaceSearchSliceResponse = StoredPlaceSearchSliceResponse(
            items = view.items.map(StoredPlaceSearchResponse::from),
            page = view.page,
            size = view.size,
            hasNext = view.hasNext,
        )
    }
}

data class StoredPlaceSearchResponse(
    @field:Schema(description = "장소 식별자")
    val id: Long,
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
    @field:Schema(description = "장소 대표 썸네일 URL", nullable = true)
    val thumbnailUrl: String?,
    @field:Schema(description = "장소 대표 태그 목록(최대 4개)")
    val tags: List<String>,
    @field:Schema(description = "현재 사용자의 저장 여부")
    val bookmarked: Boolean,
) {
    companion object {
        fun from(view: StoredPlaceSearchView): StoredPlaceSearchResponse = StoredPlaceSearchResponse(
            id = view.id,
            name = view.name,
            address = view.address,
            category = view.category,
            latitude = view.latitude,
            longitude = view.longitude,
            thumbnailUrl = view.thumbnailUrl,
            tags = view.tags,
            bookmarked = view.bookmarked,
        )
    }
}
