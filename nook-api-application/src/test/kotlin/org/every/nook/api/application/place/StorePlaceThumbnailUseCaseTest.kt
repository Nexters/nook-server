package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class StorePlaceThumbnailUseCaseTest {
    @Test
    fun `fetches and updates a thumbnail after place parsing`() {
        val updates = mutableListOf<String>()
        val place = PlaceCandidate(
            provider = "KAKAO",
            externalPlaceId = "123",
            name = "Nook Cafe",
            address = "Seoul",
            latitude = BigDecimal("37.1"),
            longitude = BigDecimal("127.1"),
            category = null,
            phoneNumber = null,
            providerUrl = null,
        )
        val useCase = StorePlaceThumbnailUseCase(
            thumbnailProvider = PlaceThumbnailProvider { "https://cdn.example.com/place.jpg" },
            updatePort = PlaceThumbnailUpdatePort { provider, externalPlaceId, thumbnailUrl ->
                updates += "$provider:$externalPlaceId:$thumbnailUrl"
            },
        )

        useCase(11, place)

        assertEquals(listOf("KAKAO:123:https://cdn.example.com/place.jpg"), updates)
    }
}
