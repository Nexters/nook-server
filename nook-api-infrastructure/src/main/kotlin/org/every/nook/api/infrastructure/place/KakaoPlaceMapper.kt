package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import java.math.BigDecimal

class KakaoPlaceMapper {
    fun map(response: KakaoPlaceResponse): List<PlaceCandidate> = response.documents.map { document ->
        PlaceCandidate(
            provider = PROVIDER,
            externalPlaceId = document.id.requireValue(),
            name = document.placeName.requireValue(),
            address = document.roadAddressName.orFallback(document.addressName).requireValue(),
            latitude = document.y.toCoordinate(),
            longitude = document.x.toCoordinate(),
            category = document.categoryName.toTopLevelCategory(),
            phoneNumber = document.phone.toNullableValue(),
            providerUrl = document.placeUrl.toNullableValue(),
            distanceMeters = document.distance.toNullableValue()?.toIntOrNull(),
        )
    }

    private fun String?.orFallback(fallback: String?): String? = takeUnless { it.isNullOrBlank() } ?: fallback

    private fun String?.requireValue(): String = requireNotNull(toNullableValue())

    private fun String?.toCoordinate(): BigDecimal = requireValue().toBigDecimal()

    private fun String?.toNullableValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String?.toTopLevelCategory(): String? = toNullableValue()
        ?.substringBefore(CATEGORY_DELIMITER)
        ?.toNullableValue()

    private companion object {
        const val PROVIDER = "KAKAO"
        const val CATEGORY_DELIMITER = ">"
    }
}
