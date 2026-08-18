package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.SavedPlaceSearchPort

class SearchSavedPlacesUseCase(private val searchPort: SavedPlaceSearchPort) {
    operator fun invoke(query: Query): SavedPlaceSearchPageView {
        if (query.hasInvalidKeyword() || query.hasInvalidPagination()) {
            throw InvalidPlaceSearchRequestException()
        }
        return searchPort.search(
            userId = query.userId,
            keyword = query.keyword.trim(),
            page = query.page,
            size = query.size,
        )
    }

    data class Query(val userId: Long, val keyword: String, val page: Int, val size: Int)

    private fun Query.hasInvalidKeyword(): Boolean = keyword.isBlank() || keyword.length > MAX_QUERY_LENGTH

    private fun Query.hasInvalidPagination(): Boolean = page < 0 || size !in 1..MAX_PAGE_SIZE

    companion object {
        const val MAX_QUERY_LENGTH = 100
        const val MAX_PAGE_SIZE = 100
    }
}
