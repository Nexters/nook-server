package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.port.SavedPostQueryPort

class GetSavedPostDetailUseCase(private val savedPostQueryPort: SavedPostQueryPort) {
    operator fun invoke(query: Query): SavedPostDetail =
        savedPostQueryPort.findDetail(query.userId, query.postId) ?: throw PostNotFoundException()

    data class Query(val userId: Long, val postId: Long)
}
