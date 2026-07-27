package org.every.nook.api.application.post.port

fun interface UpdatePostMemoPort {
    fun update(userId: Long, postId: Long, memo: String?): Boolean
}
