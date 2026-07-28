package org.every.nook.api.application.post.port

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostContentParsingStatus

fun interface CreatePostPort {
    fun create(userId: Long, post: Post, memo: String?, groupIds: Set<Long>): CreatedPost
}

data class CreatedPost(
    val postId: Long,
    val contentParsingStatus: PostContentParsingStatus,
    val placeParsingStatus: PlaceParsingStatus?,
) {
    constructor(postId: Long, placeParsingStatus: PlaceParsingStatus) : this(
        postId = postId,
        contentParsingStatus = PostContentParsingStatus.COMPLETED,
        placeParsingStatus = placeParsingStatus,
    )
}
