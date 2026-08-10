package org.every.nook.api.presentation.place.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceOpeningPeriod
import org.every.nook.api.application.place.PlaceOpeningPoint
import org.every.nook.api.application.place.PlacePostGroupView
import org.every.nook.api.application.place.PlacePostMediaView
import org.every.nook.api.application.place.PlacePostPageView
import org.every.nook.api.application.place.PlacePostView
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.presentation.response.toSeoulOffsetDateTime
import java.math.BigDecimal
import java.time.OffsetDateTime

data class PlaceDetailResponse(
    @field:Schema(description = "장소 식별자")
    val id: Long,
    @field:Schema(description = "장소 provider")
    val provider: String,
    @field:Schema(description = "provider의 장소 식별자")
    val externalPlaceId: String,
    @field:Schema(description = "장소명")
    val name: String,
    @field:Schema(description = "장소 주소")
    val address: String,
    @field:Schema(description = "장소 위도")
    val latitude: BigDecimal,
    @field:Schema(description = "장소 경도")
    val longitude: BigDecimal,
    @field:Schema(description = "장소 카테고리", nullable = true)
    val category: String?,
    @field:Schema(description = "장소 전화번호", nullable = true)
    val phoneNumber: String?,
    @field:Schema(description = "장소 대표 썸네일 URL", nullable = true)
    val thumbnailUrl: String?,
    @field:Schema(description = "장소 썸네일 파싱 상태")
    val thumbnailParsingStatus: PlaceThumbnailParsingStatusView,
    @field:Schema(description = "장소 사진 URL 목록(최대 6장)")
    val photoUrls: List<String>,
    @field:Schema(description = "장소 정규 영업시간", nullable = true)
    val openingHours: PlaceOpeningHoursResponse?,
    @field:Schema(description = "현재 영업 여부", nullable = true)
    val openNow: Boolean?,
    @field:Schema(description = "장소 대표 태그 목록(최대 4개)")
    val tags: List<String>,
    @field:Schema(description = "사용자의 장소 북마크 여부")
    val bookmarked: Boolean,
    @field:Schema(description = "이 장소와 연결된 저장 게시물 페이지")
    val posts: PlacePostPageResponse,
) {
    companion object {
        fun from(view: PlaceDetailView): PlaceDetailResponse = PlaceDetailResponse(
            id = view.id,
            provider = view.provider,
            externalPlaceId = view.externalPlaceId,
            name = view.name,
            address = view.address,
            latitude = view.latitude,
            longitude = view.longitude,
            category = view.category,
            phoneNumber = view.phoneNumber,
            thumbnailUrl = view.thumbnailUrl,
            thumbnailParsingStatus = view.thumbnailParsingStatus,
            photoUrls = view.photoUrls,
            openingHours = view.openingHours?.let(PlaceOpeningHoursResponse::from),
            openNow = view.openNow,
            tags = view.tags,
            bookmarked = view.bookmarked,
            posts = PlacePostPageResponse.from(view.posts),
        )
    }
}

data class PlaceOpeningHoursResponse(
    @field:Schema(description = "IANA 시간대")
    val timeZone: String,
    @field:Schema(description = "구조화된 주간 영업 구간")
    val periods: List<PlaceOpeningPeriodResponse>,
    @field:Schema(description = "Google이 제공한 현지화된 요일별 영업시간")
    val weekdayDescriptions: List<String>,
) {
    companion object {
        fun from(hours: PlaceOpeningHours): PlaceOpeningHoursResponse = PlaceOpeningHoursResponse(
            timeZone = hours.timeZone,
            periods = hours.periods.map(PlaceOpeningPeriodResponse::from),
            weekdayDescriptions = hours.weekdayDescriptions,
        )
    }
}

data class PlaceOpeningPeriodResponse(val open: PlaceOpeningPointResponse, val close: PlaceOpeningPointResponse?) {
    companion object {
        fun from(period: PlaceOpeningPeriod): PlaceOpeningPeriodResponse = PlaceOpeningPeriodResponse(
            open = PlaceOpeningPointResponse.from(period.open),
            close = period.close?.let(PlaceOpeningPointResponse::from),
        )
    }
}

data class PlaceOpeningPointResponse(val day: Int, val hour: Int, val minute: Int) {
    companion object {
        fun from(point: PlaceOpeningPoint): PlaceOpeningPointResponse = PlaceOpeningPointResponse(
            day = point.day,
            hour = point.hour,
            minute = point.minute,
        )
    }
}

data class PlacePostPageResponse(
    @field:Schema(description = "저장 게시물 목록")
    val items: List<PlacePostResponse>,
    @field:Schema(description = "현재 페이지 번호")
    val page: Int,
    @field:Schema(description = "페이지당 게시물 수")
    val size: Int,
    @field:Schema(description = "전체 게시물 수")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수")
    val totalPages: Int,
    @field:Schema(description = "다음 페이지 존재 여부")
    val hasNext: Boolean,
) {
    companion object {
        fun from(view: PlacePostPageView): PlacePostPageResponse = PlacePostPageResponse(
            items = view.items.map(PlacePostResponse::from),
            page = view.page,
            size = view.size,
            totalElements = view.totalElements,
            totalPages = view.totalPages,
            hasNext = view.hasNext,
        )
    }
}

data class PlacePostResponse(
    @field:Schema(description = "저장 게시물 식별자")
    val postId: Long,
    @field:Schema(description = "게시물 제목", nullable = true)
    val title: String?,
    @field:Schema(description = "게시물 작성자 식별자", nullable = true)
    val authorIdentifier: String?,
    @field:Schema(description = "대표 미디어", nullable = true)
    val representativeMedia: PlacePostMediaResponse?,
    @field:Schema(description = "사용자 메모", nullable = true)
    val memo: String?,
    @field:Schema(description = "게시물 저장 시각")
    val savedAt: OffsetDateTime,
    @field:Schema(description = "게시물이 속한 그룹 목록")
    val groups: List<PlacePostGroupResponse>,
) {
    companion object {
        fun from(view: PlacePostView): PlacePostResponse = PlacePostResponse(
            postId = view.postId,
            title = view.title,
            authorIdentifier = view.authorIdentifier,
            representativeMedia = view.representativeMedia?.let(PlacePostMediaResponse::from),
            memo = view.memo,
            savedAt = view.savedAt.toSeoulOffsetDateTime(),
            groups = view.groups.map(PlacePostGroupResponse::from),
        )
    }
}

data class PlacePostGroupResponse(
    @field:Schema(description = "그룹 식별자")
    val id: Long,
    @field:Schema(description = "그룹명")
    val name: String,
    @field:Schema(description = "그룹 색상 코드")
    val color: String,
) {
    companion object {
        fun from(view: PlacePostGroupView): PlacePostGroupResponse = PlacePostGroupResponse(
            id = view.id,
            name = view.name,
            color = view.color,
        )
    }
}

data class PlacePostMediaResponse(
    @field:Schema(description = "미디어 유형")
    val type: String,
    @field:Schema(description = "미디어 URL")
    val url: String,
) {
    companion object {
        fun from(view: PlacePostMediaView): PlacePostMediaResponse = PlacePostMediaResponse(
            type = view.type.name,
            url = view.url,
        )
    }
}
