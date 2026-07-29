package org.every.nook.api.application.place

import java.math.BigDecimal

class SearchPlacesUseCase(
    private val provider: PagedPlaceSearchProvider,
    private val selectionTokenPort: PlaceSelectionTokenPort,
) {
    operator fun invoke(query: Query): PlaceSearchSliceView {
        query.validate()
        val page = provider.searchPage(
            PlaceSearchProvider.Request(
                query = query.keyword.trim(),
                longitude = query.longitude,
                latitude = query.latitude,
                page = query.page + PROVIDER_PAGE_OFFSET,
                size = query.size,
            ),
        )
        return PlaceSearchSliceView(
            items = page.items.map { candidate ->
                PlaceSearchResultView(
                    selectionToken = selectionTokenPort.issue(query.userId, candidate),
                    candidate = candidate,
                )
            },
            page = query.page,
            size = query.size,
            hasNext = page.hasNext,
        )
    }

    data class Query(
        val userId: Long,
        val keyword: String,
        val page: Int,
        val size: Int,
        val longitude: BigDecimal?,
        val latitude: BigDecimal?,
    ) {
        fun validate() {
            if (hasInvalidKeyword() || hasInvalidPagination() || hasInvalidCoordinates()) {
                throw InvalidPlaceSearchRequestException()
            }
        }

        private fun hasInvalidKeyword(): Boolean = keyword.isBlank() || keyword.length > MAX_QUERY_LENGTH

        private fun hasInvalidPagination(): Boolean = page !in 0 until MAX_PAGE_COUNT || size !in 1..MAX_PAGE_SIZE

        private fun hasInvalidCoordinates(): Boolean = (longitude == null) != (latitude == null) ||
            (longitude != null && longitude !in MIN_LONGITUDE..MAX_LONGITUDE) ||
            (latitude != null && latitude !in MIN_LATITUDE..MAX_LATITUDE)
    }

    companion object {
        const val MAX_PAGE_SIZE = 15
        const val MAX_PAGE_COUNT = 45
        const val MAX_QUERY_LENGTH = 100
        private const val PROVIDER_PAGE_OFFSET = 1
        private val MIN_LONGITUDE = BigDecimal("-180")
        private val MAX_LONGITUDE = BigDecimal("180")
        private val MIN_LATITUDE = BigDecimal("-90")
        private val MAX_LATITUDE = BigDecimal("90")
    }
}

data class PlaceSearchResultView(val selectionToken: String, val candidate: PlaceCandidate)

data class PlaceSearchSliceView(
    val items: List<PlaceSearchResultView>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)
