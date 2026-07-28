package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPostQueryPort

class ListGroupPostsUseCase(private val groupPostQueryPort: GroupPostQueryPort) {
    operator fun invoke(query: Query): GroupPostPage = groupPostQueryPort.findAll(
        userId = query.userId,
        groupId = query.groupId,
        page = query.page,
        size = query.size,
    ) ?: throw GroupNotFoundException()

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}
