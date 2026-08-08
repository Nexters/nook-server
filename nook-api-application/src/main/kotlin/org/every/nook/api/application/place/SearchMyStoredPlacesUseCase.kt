package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.SearchMyStoredPlacesPort

class SearchMyStoredPlacesUseCase(private val port: SearchMyStoredPlacesPort) {
    operator fun invoke(query: Query): StoredPlaceSearchSliceView {
        val keyword = query.keyword.trim()
        require(keyword.isNotEmpty()) { "Place search keyword must not be blank" }
        require(query.page >= 0) { "Place search page is invalid" }
        require(query.size in 1..MAX_STORED_PLACE_SEARCH_PAGE_SIZE) { "Place search page size is invalid" }
        val rows = port.searchMine(query.userId, keyword, Math.multiplyExact(query.page, query.size), query.size + 1)
        return rows.toSlice(query.page, query.size)
    }

    data class Query(val userId: Long, val keyword: String, val page: Int, val size: Int)
}
