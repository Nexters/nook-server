package org.every.nook.api.application.post.port

import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus

fun interface FindPostPlaceParsingPort {
    fun find(userId: Long, postId: Long): PostPlaceParsingSnapshot?
}

data class PostPlaceParsingSnapshot(
    val postId: Long,
    val placeParsingStatus: PlaceParsingStatus,
    val failureReason: String?,
    val places: List<RelatedPlace>,
) {
    data class RelatedPlace(
        val place: Place,
        val bookmarked: Boolean,
        val thumbnailUrl: String?,
        val thumbnailParsingStatus: PlaceThumbnailParsingStatusView,
        val tags: List<String> = emptyList(),
        val memo: String?,
    )
}
