package org.every.nook.api.application.post

import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort

class ListSavedPostsUseCase(private val savedPostQueryPort: SavedPostQueryPort) {
    operator fun invoke(query: Query): SavedPostPage = savedPostQueryPort.findAll(query.userId, query.page, query.size)

    data class Query(val userId: Long, val page: Int, val size: Int)
}
