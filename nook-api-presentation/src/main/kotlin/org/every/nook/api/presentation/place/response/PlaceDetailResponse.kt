package org.every.nook.api.presentation.place.response

import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.PlacePostMediaView
import org.every.nook.api.application.place.PlacePostPageView
import org.every.nook.api.application.place.PlacePostView
import java.math.BigDecimal
import java.time.Instant

data class PlaceDetailResponse(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val bookmarked: Boolean,
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
            bookmarked = view.bookmarked,
            posts = PlacePostPageResponse.from(view.posts),
        )
    }
}

data class PlacePostPageResponse(
    val items: List<PlacePostResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
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
    val postId: Long,
    val title: String?,
    val authorIdentifier: String?,
    val representativeMedia: PlacePostMediaResponse?,
    val memo: String?,
    val savedAt: Instant,
) {
    companion object {
        fun from(view: PlacePostView): PlacePostResponse = PlacePostResponse(
            postId = view.postId,
            title = view.title,
            authorIdentifier = view.authorIdentifier,
            representativeMedia = view.representativeMedia?.let(PlacePostMediaResponse::from),
            memo = view.memo,
            savedAt = view.savedAt,
        )
    }
}

data class PlacePostMediaResponse(val type: String, val url: String) {
    companion object {
        fun from(view: PlacePostMediaView): PlacePostMediaResponse = PlacePostMediaResponse(
            type = view.type.name,
            url = view.url,
        )
    }
}
