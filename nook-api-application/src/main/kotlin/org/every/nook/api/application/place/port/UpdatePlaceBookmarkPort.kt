package org.every.nook.api.application.place.port

fun interface UpdatePlaceBookmarkPort {
    fun update(userId: Long, placeId: Long, bookmarked: Boolean): PlaceBookmarkUpdateResult
}

data class PlaceBookmarkUpdateResult(val accessible: Boolean, val changed: Boolean)
