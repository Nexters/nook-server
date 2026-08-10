package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPlaceQueryPort

class ListGroupPlacesUseCase(private val groupPlaceQueryPort: GroupPlaceQueryPort) {
    operator fun invoke(query: Query): GroupPlacePage = groupPlaceQueryPort.findPlaces(
        userId = query.userId,
        groupId = query.groupId,
        page = query.page,
        size = query.size,
    ) ?: throw GroupNotFoundException()

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}
