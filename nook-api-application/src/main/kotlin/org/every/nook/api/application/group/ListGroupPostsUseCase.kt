package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPostQueryPort

class ListGroupPostsUseCase(
    private val groupPostQueryPort: GroupPostQueryPort,
    private val groupReadAccessPort: org.every.nook.api.application.group.port.GroupReadAccessPort =
        org.every.nook.api.application.group.port.GroupReadAccessPort { memberId, _ -> memberId },
) {
    operator fun invoke(query: Query): GroupPostPage = groupPostQueryPort.findAll(
        userId = groupReadAccessPort.resolveOwnerId(query.userId, query.groupId) ?: throw GroupNotFoundException(),
        groupId = query.groupId,
        page = query.page,
        size = query.size,
    ) ?: throw GroupNotFoundException()

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}
