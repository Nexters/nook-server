package org.every.nook.api.domain.place

import java.math.BigDecimal

data class GeoPoint(val latitude: BigDecimal, val longitude: BigDecimal) {
    init {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE) {
            "Latitude must be between $MIN_LATITUDE and $MAX_LATITUDE"
        }
        require(longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "Longitude must be between $MIN_LONGITUDE and $MAX_LONGITUDE"
        }
    }

    companion object {
        val MIN_LATITUDE: BigDecimal = BigDecimal("-90")
        val MAX_LATITUDE: BigDecimal = BigDecimal("90")
        val MIN_LONGITUDE: BigDecimal = BigDecimal("-180")
        val MAX_LONGITUDE: BigDecimal = BigDecimal("180")
    }
}
