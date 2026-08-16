package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FixedPlaceThumbnailProviderTest {
    @Test
    fun `returns configured bucket image without place-specific data`() {
        val provider = FixedPlaceThumbnailProvider("https://cdn.example.com/fallback.jpg")

        val result = provider.fetch(
            PlaceCandidate(
                provider = "kakao",
                externalPlaceId = "place-id",
                name = "장소",
                address = "서울시",
                latitude = BigDecimal("37.0"),
                longitude = BigDecimal("127.0"),
                category = "음식점",
                phoneNumber = null,
                providerUrl = null,
            ),
        )

        assertEquals(listOf("https://cdn.example.com/fallback.jpg"), result.photoUrls)
        assertNull(result.googlePlaceId)
        assertNull(result.openingHours)
    }
}
