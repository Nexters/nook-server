package org.every.nook.api.application.place

import java.math.BigDecimal
import java.time.Instant

data class MapPlaceView(
    val id: Long,
    val name: String,
    val city: String?,
    val category: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val color: String,
    val thumbnailUrl: String?,
    val thumbnailParsingStatus: PlaceThumbnailParsingStatusView = PlaceThumbnailParsingStatusView.PENDING,
    val tags: List<String> = emptyList(),
)

data class RecentPlaceView(
    val bookmarkId: Long,
    val bookmarkedAt: Instant,
    val id: Long,
    val name: String,
    val city: String?,
    val address: String,
    val category: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val thumbnailUrl: String?,
    val thumbnailParsingStatus: PlaceThumbnailParsingStatusView = PlaceThumbnailParsingStatusView.PENDING,
    val tags: List<String> = emptyList(),
    val accessType: PlaceAccessType = PlaceAccessType.OWNED,
    val shareToken: String? = null,
)

/**
 * 내 저장 게시물로 접근하는 장소는 OWNED, 공유 그룹 구독으로만 접근하는 장소는 SHARED다.
 * SHARED 장소의 상세는 공유 토큰이 있어야 조회할 수 있다.
 */
enum class PlaceAccessType { OWNED, SHARED }

data class RecentPlaceSliceView(
    val items: List<RecentPlaceView>,
    val nextCursor: RecentPlaceCursor?,
    val hasNext: Boolean,
)

data class RecentPlaceCursor(val bookmarkedAt: Instant, val bookmarkId: Long)
