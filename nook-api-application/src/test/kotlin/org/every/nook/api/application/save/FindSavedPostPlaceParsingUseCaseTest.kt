package org.every.nook.api.application.save

import org.every.nook.api.application.save.error.SavedPostNotFoundException
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.port.FindSavedPostPlaceParsingPort
import org.every.nook.api.application.save.port.SavedPostPlaceParsingSnapshot
import org.every.nook.api.domain.place.GeoPoint
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceProviderReference
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindSavedPostPlaceParsingUseCaseTest {
    @Test
    fun `returns completed parsing with places`() {
        val place = Place(
            providerReference = PlaceProviderReference("KAKAO", "123"),
            name = "Nook Cafe",
            address = "Seoul",
            location = GeoPoint(BigDecimal("37.1"), BigDecimal("127.1")),
            id = 17,
        )
        val port = FindSavedPostPlaceParsingPort { userId, savedPostId ->
            assertEquals(7, userId)
            assertEquals(11, savedPostId)
            SavedPostPlaceParsingSnapshot(
                savedPostId = savedPostId,
                postId = 13,
                placeParsingStatus = PlaceParsingStatus.COMPLETED,
                failureReason = null,
                places = listOf(SavedPostPlaceParsingSnapshot.SavedPlace(place, bookmarked = true)),
            )
        }
        val useCase = FindSavedPostPlaceParsingUseCase(port)

        val result = useCase(FindSavedPostPlaceParsingUseCase.Query(7, 11))

        assertEquals(PlaceParsingStatusView.COMPLETED, result.placeParsingStatus)
        assertEquals("Nook Cafe", result.places.single().name)
        assertEquals(true, result.places.single().bookmarked)
    }

    @Test
    fun `hides missing or unauthorized saved posts behind not found`() {
        val useCase = FindSavedPostPlaceParsingUseCase(
            FindSavedPostPlaceParsingPort { _, _ -> null },
        )

        assertFailsWith<SavedPostNotFoundException> {
            useCase(FindSavedPostPlaceParsingUseCase.Query(7, 11))
        }
    }
}
