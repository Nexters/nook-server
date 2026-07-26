package org.every.nook.api.application.save.port

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post

fun interface SaveInstagramPostPort {
    fun save(userId: Long, post: Post): SavedInstagramPost
}

data class SavedInstagramPost(val savedPostId: Long, val postId: Long, val placeParsingStatus: PlaceParsingStatus)
