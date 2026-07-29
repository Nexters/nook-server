package org.every.nook.api.domain.place

import java.math.BigDecimal

data class GeoBounds(
    val northLatitude: BigDecimal,
    val westLongitude: BigDecimal,
    val southLatitude: BigDecimal,
    val eastLongitude: BigDecimal,
) {
    init {
        require(northLatitude in GeoPoint.MIN_LATITUDE..GeoPoint.MAX_LATITUDE)
        require(southLatitude in GeoPoint.MIN_LATITUDE..GeoPoint.MAX_LATITUDE)
        require(westLongitude in GeoPoint.MIN_LONGITUDE..GeoPoint.MAX_LONGITUDE)
        require(eastLongitude in GeoPoint.MIN_LONGITUDE..GeoPoint.MAX_LONGITUDE)
        require(northLatitude >= southLatitude) { "North latitude must be greater than or equal to south latitude" }
        require(westLongitude <= eastLongitude) { "West longitude must be less than or equal to east longitude" }
    }
}
