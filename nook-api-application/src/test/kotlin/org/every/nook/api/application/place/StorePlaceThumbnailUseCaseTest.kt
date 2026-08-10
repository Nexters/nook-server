package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
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
            thumbnailProvider = PlaceThumbnailProvider {
                PlaceSupplement(null, listOf("https://cdn.example.com/place.jpg"))
            },
            updatePort = FakeThumbnailUpdatePort(updates),
        )

        useCase(11, place)

        assertEquals(listOf("KAKAO:123:https://cdn.example.com/place.jpg"), updates)
    }

    private class FakeThumbnailUpdatePort(private val updates: MutableList<String>) : PlaceThumbnailUpdatePort {
        var status: PlaceThumbnailParsingStatus = PlaceThumbnailParsingStatus.PENDING

        override fun update(
            provider: String,
            externalPlaceId: String,
            status: PlaceThumbnailParsingStatus,
            supplement: PlaceSupplement?,
        ) {
            this.status = status
            if (status == PlaceThumbnailParsingStatus.COMPLETED) {
                updates += "$provider:$externalPlaceId:${requireNotNull(supplement).photoUrls.first()}"
            }
        }
    }
}
