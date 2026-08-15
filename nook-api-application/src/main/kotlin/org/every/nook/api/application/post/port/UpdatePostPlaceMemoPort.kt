package org.every.nook.api.application.post.port

fun interface UpdatePostPlaceMemoPort {
    fun update(userId: Long, postId: Long, placeId: Long, memo: String?): Boolean
}
