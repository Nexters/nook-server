package org.every.nook.api.application.post

import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.UnlinkedSavedPostQueryPort

class ListUnlinkedSavedPostsUseCase(private val queryPort: UnlinkedSavedPostQueryPort) {
    operator fun invoke(query: Query): SavedPostPage {
        require(query.page >= 0 && query.size in 1..MAX_PAGE_SIZE) { "Invalid unlinked post pagination" }
        return queryPort.findUnlinked(query.userId, query.page, query.size)
    }

    data class Query(val userId: Long, val page: Int, val size: Int)

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
