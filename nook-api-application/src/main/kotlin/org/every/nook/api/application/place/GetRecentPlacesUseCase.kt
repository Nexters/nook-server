package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.PlaceMapQueryPort

class GetRecentPlacesUseCase(private val queryPort: PlaceMapQueryPort) {
    operator fun invoke(query: Query): RecentPlaceSliceView {
        require(query.size in 1..MAX_PAGE_SIZE) { "Recent place page size is invalid" }
        query.ordering.validate()
        if (query.cursor != null && !query.ordering.matches(query.cursor)) {
            throw InvalidPlaceSearchRequestException()
        }
        val rows = queryPort.findRecent(
            userId = query.userId,
            cursor = query.cursor,
            limit = query.size + 1,
            groupIds = query.groupIds,
            ordering = query.ordering,
        )
        val hasNext = rows.size > query.size
        val items = rows.take(query.size)
        val nextCursor = if (hasNext) {
            items.lastOrNull()?.let {
                RecentPlaceCursor(
                    bookmarkedAt = it.bookmarkedAt,
                    bookmarkId = it.bookmarkId,
                    sort = query.ordering.sort,
                    latitude = query.ordering.latitude,
                    longitude = query.ordering.longitude,
                    distanceScore = it.distanceScore,
                )
            }
        } else {
            null
        }
        return RecentPlaceSliceView(
            items = items,
            nextCursor = nextCursor,
            hasNext = hasNext,
            totalCount = queryPort.countRecent(query.userId, query.groupIds),
        )
    }

    data class Query(
        val userId: Long,
        val cursor: RecentPlaceCursor?,
        val size: Int,
        val groupIds: List<Long>? = null,
        val ordering: PlaceOrdering = PlaceOrdering(),
    )

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
