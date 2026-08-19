package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorePlaceThumbnailUseCaseTest {
    @Test
    fun `fetches and updates a thumbnail after place parsing`() {
        val updates = mutableListOf<String>()
        val requests = mutableListOf<PlaceThumbnailProvider.Request>()
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
            thumbnailProvider = PlaceThumbnailProvider { request ->
                requests += request
                PlaceSupplement(null, listOf("https://cdn.example.com/place.jpg"))
            },
            updatePort = FakeThumbnailUpdatePort(updates),
        )

        useCase(11, listOf(PlaceThumbnailProvider.Request(place, sourcePostId = 11, sourceMediaSequence = 1)))

        assertEquals(11, requests.single().sourcePostId)
        assertEquals(1, requests.single().sourceMediaSequence)
        assertEquals(listOf("KAKAO:123:https://cdn.example.com/place.jpg"), updates)
    }

    @Test
    fun `marks thumbnail as failed when completion update fails`() {
        val statuses = mutableListOf<PlaceThumbnailParsingStatus>()
        val place = place()
        val useCase = StorePlaceThumbnailUseCase(
            thumbnailProvider = PlaceThumbnailProvider { PlaceSupplement(null, emptyList()) },
            updatePort = object : PlaceThumbnailUpdatePort {
                override fun update(
                    provider: String,
                    externalPlaceId: String,
                    status: PlaceThumbnailParsingStatus,
                    supplement: PlaceSupplement?,
                ) {
                    statuses += status
                    if (status == PlaceThumbnailParsingStatus.COMPLETED) error("completion failed")
                }
            },
        )

        assertFailsWith<IllegalStateException> {
            useCase(11, listOf(PlaceThumbnailProvider.Request(place, sourcePostId = 11, sourceMediaSequence = 1)))
        }

        assertEquals(
            listOf(
                PlaceThumbnailParsingStatus.PROCESSING,
                PlaceThumbnailParsingStatus.COMPLETED,
                PlaceThumbnailParsingStatus.FAILED,
            ),
            statuses,
        )
    }

    private fun place() = PlaceCandidate(
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
