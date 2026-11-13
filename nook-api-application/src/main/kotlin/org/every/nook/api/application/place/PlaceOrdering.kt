package org.every.nook.api.application.place

import java.math.BigDecimal

enum class PlaceSort { LATEST, OLDEST, NEAREST }

data class PlaceOrdering(
    val sort: PlaceSort = PlaceSort.LATEST,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
) {
    fun validate() {
        val paired = (latitude == null) == (longitude == null)
        val validLatitude = latitude == null || latitude in MIN_LATITUDE..MAX_LATITUDE
        val validLongitude = longitude == null || longitude in MIN_LONGITUDE..MAX_LONGITUDE
        val validCoordinates = paired && validLatitude && validLongitude
        val missingOrigin = sort == PlaceSort.NEAREST && latitude == null
        if (!validCoordinates || missingOrigin) {
            throw InvalidPlaceSearchRequestException()
        }
    }

    fun matches(cursor: RecentPlaceCursor): Boolean {
        if (cursor.sort != sort) return false
        if (sort != PlaceSort.NEAREST) return true
        val validScore = cursor.distanceScore?.let { it.isFinite() && it >= 0 } == true
        return latitude.sameValue(cursor.latitude) && longitude.sameValue(cursor.longitude) && validScore
    }

    private fun BigDecimal?.sameValue(other: BigDecimal?): Boolean =
        this != null && other != null && compareTo(other) == 0

    private companion object {
        val MIN_LATITUDE = BigDecimal("-90")
        val MAX_LATITUDE = BigDecimal("90")
        val MIN_LONGITUDE = BigDecimal("-180")
        val MAX_LONGITUDE = BigDecimal("180")
    }
}
