package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort

class ListGroupPostsUseCase(private val savedPostQueryPort: SavedPostQueryPort) {
    operator fun invoke(query: Query): SavedPostPage = savedPostQueryPort.findAllByGroup(
        userId = query.userId,
        groupId = query.groupId,
        page = query.page,
        size = query.size,
    ) ?: throw GroupNotFoundException()

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}
