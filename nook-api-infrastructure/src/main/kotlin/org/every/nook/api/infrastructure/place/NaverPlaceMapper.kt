package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.springframework.web.util.HtmlUtils
import java.math.BigDecimal
import java.security.MessageDigest

class NaverPlaceMapper {
    fun map(query: String, response: NaverPlaceResponse): List<PlaceCandidate> {
        return response.items.mapNotNull { item ->
            val longitude = item.mapx.toCoordinateOrNull(MIN_LONGITUDE, MAX_LONGITUDE)
                ?: return@mapNotNull null
            val latitude = item.mapy.toCoordinateOrNull(MIN_LATITUDE, MAX_LATITUDE)
                ?: return@mapNotNull null
            val displayAddress = item.roadAddress
                .orFallback(item.address)
                .toNullableValue()
                ?: return@mapNotNull null
            val name = item.title.toPlainText() ?: query

            PlaceCandidate(
                provider = PROVIDER,
                externalPlaceId = stableId(name, displayAddress, longitude, latitude),
                name = name,
                address = displayAddress,
                latitude = latitude,
                longitude = longitude,
                category = item.category.toTopLevelCategory(),
                phoneNumber = item.telephone.toNullableValue(),
                providerUrl = item.link.toNullableValue(),
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

    private fun String?.toPlainText(): String? = toNullableValue()?.let(HtmlUtils::htmlUnescape)
        ?.replace(HTML_TAG, "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun String?.toTopLevelCategory(): String? = toPlainText()
        ?.substringBefore(CATEGORY_DELIMITER)
        ?.toNullableValue()

    private fun stableId(name: String, address: String, longitude: BigDecimal, latitude: BigDecimal): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$name|$address|${longitude.toPlainString()}|${latitude.toPlainString()}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PROVIDER = "NAVER"
        const val WGS84_SCALE = 7
        const val CATEGORY_DELIMITER = ">"
        val HTML_TAG = Regex("<[^>]+>")
        val MIN_LONGITUDE = BigDecimal("-180")
        val MAX_LONGITUDE = BigDecimal("180")
        val MIN_LATITUDE = BigDecimal("-90")
        val MAX_LATITUDE = BigDecimal("90")
    }
}
