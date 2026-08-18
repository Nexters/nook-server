package org.every.nook.api.application.place

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.place.port.SavedPlaceSearchPort

class SearchSavedPlacesUseCase(
    private val searchPort: SavedPlaceSearchPort,
    private val groupOwnershipPort: GroupOwnershipPort,
) {
    operator fun invoke(query: Query): SavedPlaceSearchPageView {
        if (query.hasInvalidKeyword() || query.hasInvalidPagination()) {
            throw InvalidPlaceSearchRequestException()
        }
        if (query.groupId != null && !groupOwnershipPort.ownsAll(query.userId, setOf(query.groupId))) {
            throw GroupNotFoundException()
        }
        return searchPort.search(
            userId = query.userId,
            keyword = query.keyword.trim(),
            groupId = query.groupId,
            page = query.page,
            size = query.size,
        )
    }

    data class Query(val userId: Long, val keyword: String, val page: Int, val size: Int, val groupId: Long? = null)

    private fun Query.hasInvalidKeyword(): Boolean = keyword.isBlank() || keyword.length > MAX_QUERY_LENGTH

    private fun Query.hasInvalidPagination(): Boolean = page < 0 || size !in 1..MAX_PAGE_SIZE

    companion object {
        const val MAX_QUERY_LENGTH = 100
        const val MAX_PAGE_SIZE = 100
    }
}
