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
)

data class RecentPlaceSliceView(
    val items: List<RecentPlaceView>,
    val nextCursor: RecentPlaceCursor?,
    val hasNext: Boolean,
)

data class RecentPlaceCursor(val bookmarkedAt: Instant, val bookmarkId: Long)
