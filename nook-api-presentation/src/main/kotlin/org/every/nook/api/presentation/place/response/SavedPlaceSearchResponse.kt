package org.every.nook.api.presentation.place.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.SavedPlaceSearchGroupView
import org.every.nook.api.application.place.SavedPlaceSearchItemView
import org.every.nook.api.application.place.SavedPlaceSearchPageView

data class SavedPlaceSearchPageResponse(
    @field:Schema(description = "내 저장 장소 검색 결과")
    val items: List<SavedPlaceSearchItemResponse>,
    @field:Schema(description = "검색 결과가 존재하는 내 그룹 목록")
    val groups: List<SavedPlaceSearchGroupResponse>,
    @field:Schema(description = "현재 페이지 번호. 0부터 시작")
    val page: Int,
    @field:Schema(description = "페이지당 장소 수")
    val size: Int,
    @field:Schema(description = "전체 검색 결과 수")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수")
    val totalPages: Int,
    @field:Schema(description = "다음 페이지 존재 여부")
    val hasNext: Boolean,
) {
    companion object {
        fun from(view: SavedPlaceSearchPageView): SavedPlaceSearchPageResponse = SavedPlaceSearchPageResponse(
            items = view.items.map(SavedPlaceSearchItemResponse::from),
            groups = view.groups.map(SavedPlaceSearchGroupResponse::from),
            page = view.page,
            size = view.size,
            totalElements = view.totalElements,
            totalPages = view.totalPages,
            hasNext = view.hasNext,
        )
    }
}

data class SavedPlaceSearchGroupResponse(
    @field:Schema(description = "그룹 식별자")
    val id: Long,
    @field:Schema(description = "그룹명")
    val name: String,
    @field:Schema(description = "그룹 색상 코드")
    val color: String,
    @field:Schema(description = "검색어와 일치하는 그룹 내 장소 수")
    val matchedPlaceCount: Long,
) {
    companion object {
        fun from(view: SavedPlaceSearchGroupView): SavedPlaceSearchGroupResponse = SavedPlaceSearchGroupResponse(
            id = view.id,
            name = view.name,
            color = view.color,
            matchedPlaceCount = view.matchedPlaceCount,
        )
    }
}

data class SavedPlaceSearchItemResponse(
    @field:Schema(description = "장소명")
    val name: String,
    @field:Schema(description = "장소 주소")
    val address: String,
    @field:Schema(description = "장소 카테고리", nullable = true)
    val category: String?,
    @field:Schema(description = "장소 대표 썸네일 URL", nullable = true)
    val thumbnailUrl: String?,
    @field:Schema(description = "장소 식별자")
    val id: Long,
) {
    companion object {
        fun from(view: SavedPlaceSearchItemView): SavedPlaceSearchItemResponse = SavedPlaceSearchItemResponse(
            name = view.name,
            address = view.address,
            category = view.category,
            thumbnailUrl = view.thumbnailUrl,
            id = view.id,
        )
    }
}
