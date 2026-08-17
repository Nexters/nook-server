package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPlaceQueryPort

class ListGroupPlacesUseCase(
    private val groupPlaceQueryPort: GroupPlaceQueryPort,
    private val groupReadAccessPort: org.every.nook.api.application.group.port.GroupReadAccessPort =
        org.every.nook.api.application.group.port.GroupReadAccessPort { memberId, _ -> memberId },
) {
    operator fun invoke(query: Query): GroupPlacePage = groupPlaceQueryPort.findPlaces(
        userId = groupReadAccessPort.resolveOwnerId(query.userId, query.groupId) ?: throw GroupNotFoundException(),
        groupId = query.groupId,
        page = query.page,
        size = query.size,
    ) ?: throw GroupNotFoundException()

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}
