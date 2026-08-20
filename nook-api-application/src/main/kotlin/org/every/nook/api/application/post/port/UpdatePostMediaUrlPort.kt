package org.every.nook.api.application.post.port

fun interface UpdatePostMediaUrlPort {
    fun update(
        postId: Long,
        sequence: Int,
        sourceUrl: String,
        storedUrl: String,
        sourceThumbnailUrl: String?,
        storedThumbnailUrl: String?,
    )
}
