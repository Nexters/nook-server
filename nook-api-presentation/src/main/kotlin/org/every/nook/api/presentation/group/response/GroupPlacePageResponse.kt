package org.every.nook.api.presentation.group.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.group.GroupPlacePage
import org.every.nook.api.application.group.GroupPlaceSummary
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import java.math.BigDecimal

data class GroupPlacePageResponse(
    @field:Schema(description = "그룹 소유자의 닉네임")
    val ownerNickname: String,
    @field:Schema(description = "그룹에 저장된 장소 목록")
    val items: List<GroupPlaceSummaryResponse>,
    @field:Schema(description = "현재 페이지 번호")
    val page: Int,
    @field:Schema(description = "페이지당 장소 수")
    val size: Int,
    @field:Schema(description = "전체 장소 수")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수")
    val totalPages: Int,
    @field:Schema(description = "다음 페이지 존재 여부")
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: GroupPlacePage): GroupPlacePageResponse = GroupPlacePageResponse(
            ownerNickname = result.ownerNickname,
            items = result.items.map(GroupPlaceSummaryResponse::from),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }
}

data class GroupPlaceSummaryResponse(
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
    @field:Schema(description = "장소 썸네일 파싱 상태")
    val thumbnailParsingStatus: PlaceThumbnailParsingStatusView,
    @field:Schema(description = "장소 대표 태그 목록(최대 4개)")
    val tags: List<String>,
) {
    companion object {
        fun from(result: GroupPlaceSummary): GroupPlaceSummaryResponse = GroupPlaceSummaryResponse(
            id = result.id,
            name = result.name,
            city = result.city,
            address = result.address,
            category = result.category,
            latitude = result.latitude,
            longitude = result.longitude,
            thumbnailUrl = result.thumbnailUrl,
            thumbnailParsingStatus = result.thumbnailParsingStatus,
            tags = result.tags,
        )
    }
}
