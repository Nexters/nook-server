package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.SearchAllStoredPlacesPort

class SearchAllStoredPlacesUseCase(private val port: SearchAllStoredPlacesPort) {
    operator fun invoke(query: Query): StoredPlaceSearchSliceView {
        val keyword = query.keyword.trim()
        require(keyword.isNotEmpty()) { "Place search keyword must not be blank" }
        require(query.page >= 0) { "Place search page is invalid" }
        require(query.size in 1..MAX_PAGE_SIZE) { "Place search page size is invalid" }
        val rows = port.searchAll(query.userId, keyword, Math.multiplyExact(query.page, query.size), query.size + 1)
        return rows.toSlice(query.page, query.size)
    }

    data class Query(val userId: Long, val keyword: String, val page: Int, val size: Int)
}

internal const val MAX_STORED_PLACE_SEARCH_PAGE_SIZE = 100

internal fun List<StoredPlaceSearchView>.toSlice(page: Int, size: Int): StoredPlaceSearchSliceView =
    StoredPlaceSearchSliceView(
        items = take(size),
        page = page,
        size = size,
        hasNext = this.size > size,
    )

private const val MAX_PAGE_SIZE = MAX_STORED_PLACE_SEARCH_PAGE_SIZE
