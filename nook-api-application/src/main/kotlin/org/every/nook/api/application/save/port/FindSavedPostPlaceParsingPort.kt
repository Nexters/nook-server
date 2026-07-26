package org.every.nook.api.application.save.port

import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus

fun interface FindSavedPostPlaceParsingPort {
    fun find(userId: Long, savedPostId: Long): SavedPostPlaceParsingSnapshot?
}

data class SavedPostPlaceParsingSnapshot(
    val savedPostId: Long,
    val postId: Long,
    val placeParsingStatus: PlaceParsingStatus,
    val failureReason: String?,
    val places: List<SavedPlace>,
) {
    data class SavedPlace(val place: Place, val bookmarked: Boolean)
}
