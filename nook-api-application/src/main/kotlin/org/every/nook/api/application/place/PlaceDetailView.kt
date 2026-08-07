package org.every.nook.api.application.place

import java.math.BigDecimal
import java.time.Instant

data class PlaceDetailView(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val thumbnailUrl: String?,
    val photoUrls: List<String> = emptyList(),
    val openingHours: PlaceOpeningHours? = null,
    val openNow: Boolean? = null,
    val tags: List<String> = emptyList(),
    val bookmarked: Boolean,
    val posts: PlacePostPageView,
)

data class PlacePostPageView(
    val items: List<PlacePostView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

data class PlacePostView(
    val postId: Long,
    val title: String?,
    val authorIdentifier: String?,
    val representativeMedia: PlacePostMediaView?,
    val memo: String?,
    val savedAt: Instant,
    val groups: List<PlacePostGroupView>,
)

data class PlacePostGroupView(val id: Long, val name: String, val color: String)

data class PlacePostMediaView(val type: PlacePostMediaTypeView, val url: String)

enum class PlacePostMediaTypeView {
    IMAGE,
    VIDEO,
}
