package org.every.nook.api.application.save.port

fun interface UpdateSavedPostPlaceBookmarkPort {
    fun update(userId: Long, savedPostId: Long, placeId: Long, bookmarked: Boolean): Boolean
}
