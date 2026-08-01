package org.every.nook.api.application.post.port

fun interface DeleteSavedPostPort {
    fun delete(userId: Long, savedPostId: Long): Boolean
}
