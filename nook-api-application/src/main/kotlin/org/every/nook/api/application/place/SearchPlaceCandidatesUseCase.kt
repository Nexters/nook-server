package org.every.nook.api.application.place

import java.math.BigDecimal

class SearchPlaceCandidatesUseCase(private val provider: PlaceSearchProvider) {
    operator fun invoke(command: Command): List<PlaceCandidate> {
        command.validate()
        return command.queries
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .flatMap { query ->
                provider.search(
                    PlaceSearchProvider.Request(
                        query = query,
                        longitude = command.longitude,
                        latitude = command.latitude,
                        radius = command.radius,
                    ),
                )
            }
            .distinctBy { candidate -> candidate.provider to candidate.externalPlaceId }
    }

    data class Command(
        val queries: List<String>,
        val longitude: BigDecimal? = null,
        val latitude: BigDecimal? = null,
        val radius: Int? = null,
    ) {
        fun validate() {
            if (hasInvalidQueries() || hasInvalidCoordinates() || hasInvalidRadius()) {
                throw InvalidPlaceSearchRequestException()
            }
        }

        private fun hasInvalidQueries(): Boolean = queries.none { it.isNotBlank() } ||
            queries.size > MAX_QUERY_COUNT ||
            queries.any { it.length > MAX_QUERY_LENGTH }

        private fun hasInvalidCoordinates(): Boolean = (longitude == null) != (latitude == null) ||
            (longitude != null && longitude !in MIN_LONGITUDE..MAX_LONGITUDE) ||
            (latitude != null && latitude !in MIN_LATITUDE..MAX_LATITUDE)

        private fun hasInvalidRadius(): Boolean {
            val value = radius ?: return false
            val hasCoordinates = longitude != null && latitude != null
            return !hasCoordinates || value !in MIN_RADIUS..MAX_RADIUS
        }
    }

    private companion object {
        const val MAX_QUERY_COUNT = 10
        const val MAX_QUERY_LENGTH = 100
        const val MIN_RADIUS = 1
        const val MAX_RADIUS = 20_000
        val MIN_LONGITUDE = BigDecimal("-180")
        val MAX_LONGITUDE = BigDecimal("180")
        val MIN_LATITUDE = BigDecimal("-90")
        val MAX_LATITUDE = BigDecimal("90")
    }
}
