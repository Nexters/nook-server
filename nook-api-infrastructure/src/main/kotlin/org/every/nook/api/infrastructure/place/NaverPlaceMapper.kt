package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import java.math.BigDecimal

class NaverPlaceMapper {
    fun map(query: String, response: NaverPlaceResponse): List<PlaceCandidate> {
        return response.addresses.mapNotNull { address ->
            val longitude = address.x.toCoordinateOrNull(MIN_LONGITUDE, MAX_LONGITUDE)
                ?: return@mapNotNull null
            val latitude = address.y.toCoordinateOrNull(MIN_LATITUDE, MAX_LATITUDE)
                ?: return@mapNotNull null
            val displayAddress = address.roadAddress
                .orFallback(address.jibunAddress)
                .orFallback(address.address)
                .toNullableValue()
                ?: return@mapNotNull null

            PlaceCandidate(
                provider = PROVIDER,
                externalPlaceId = listOf(
                    displayAddress,
                    longitude.toPlainString(),
                    latitude.toPlainString(),
                ).joinToString("|"),
                name = query,
                address = displayAddress,
                latitude = latitude,
                longitude = longitude,
                category = null,
                phoneNumber = null,
                providerUrl = null,
            )
        }
    }

    private fun String?.orFallback(fallback: String?): String? = takeUnless { it.isNullOrBlank() } ?: fallback

    private fun String?.toCoordinateOrNull(min: BigDecimal, max: BigDecimal): BigDecimal? {
        val raw = toNullableValue()?.toBigDecimalOrNull() ?: return null
        if (raw in min..max) {
            return raw
        }

        val scaled = raw.movePointLeft(WGS84_SCALE)
        return scaled.takeIf { it in min..max }
    }

    private fun String?.toNullableValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val PROVIDER = "NAVER"
        const val WGS84_SCALE = 7
        val MIN_LONGITUDE = BigDecimal("-180")
        val MAX_LONGITUDE = BigDecimal("180")
        val MIN_LATITUDE = BigDecimal("-90")
        val MAX_LATITUDE = BigDecimal("90")
    }
}
