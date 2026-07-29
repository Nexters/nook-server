package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.PlaceMapQueryPort

class GetRecentPlacesUseCase(private val queryPort: PlaceMapQueryPort) {
    operator fun invoke(query: Query): RecentPlaceSliceView {
        require(query.size in 1..MAX_PAGE_SIZE) { "Recent place page size is invalid" }
        val rows = queryPort.findRecent(
            userId = query.userId,
            cursor = query.cursor,
            limit = query.size + 1,
        )
        val hasNext = rows.size > query.size
        val items = rows.take(query.size)
        val nextCursor = if (hasNext) {
            items.lastOrNull()?.let { RecentPlaceCursor(it.bookmarkedAt, it.bookmarkId) }
        } else {
            null
        }
        return RecentPlaceSliceView(items = items, nextCursor = nextCursor, hasNext = hasNext)
    }

    data class Query(val userId: Long, val cursor: RecentPlaceCursor?, val size: Int)

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
