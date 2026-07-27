package org.every.nook.api.application.post.port

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post

fun interface CreatePostPort {
    fun create(userId: Long, post: Post, memo: String?): CreatedPost
}

data class CreatedPost(val postId: Long, val placeParsingStatus: PlaceParsingStatus)
