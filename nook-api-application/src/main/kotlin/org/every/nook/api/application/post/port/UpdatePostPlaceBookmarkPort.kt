package org.every.nook.api.application.post.port

fun interface UpdatePostPlaceBookmarkPort {
    fun update(userId: Long, postId: Long, placeId: Long, bookmarked: Boolean): Boolean
}
