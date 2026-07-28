package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort
import org.every.nook.api.application.post.port.PostPlaceParsingSnapshot
import org.every.nook.api.domain.place.GeoPoint
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceProviderReference
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FindPostPlaceParsingUseCaseTest {
    @Test
    fun `returns completed parsing with places`() {
        val place = Place(
            providerReference = PlaceProviderReference("KAKAO", "123"),
            name = "Nook Cafe",
            address = "Seoul",
            location = GeoPoint(BigDecimal("37.1"), BigDecimal("127.1")),
            id = 17,
        )
        val port = FindPostPlaceParsingPort { userId, postId ->
            assertEquals(7, userId)
            assertEquals(11, postId)
            PostPlaceParsingSnapshot(
                postId = postId,
                placeParsingStatus = PlaceParsingStatus.COMPLETED,
                failureReason = null,
                places = listOf(PostPlaceParsingSnapshot.RelatedPlace(place, bookmarked = true)),
            )
        }
        val useCase = FindPostPlaceParsingUseCase(port)

        val result = useCase(FindPostPlaceParsingUseCase.Query(7, 11))

        assertEquals(PlaceParsingStatusView.COMPLETED, result.placeParsingStatus)
        assertEquals("Nook Cafe", result.places.single().name)
        assertEquals(true, result.places.single().bookmarked)
    }

    @Test
    fun `hides missing or unauthorized posts behind not found`() {
        val useCase = FindPostPlaceParsingUseCase(
            FindPostPlaceParsingPort { _, _ -> null },
        )

        assertFailsWith<PostNotFoundException> {
            useCase(FindPostPlaceParsingUseCase.Query(7, 11))
        }
    }

    @Test
    fun `hides a retained retry failure reason until the job is failed`() {
        val useCase = FindPostPlaceParsingUseCase(
            FindPostPlaceParsingPort { _, postId ->
                PostPlaceParsingSnapshot(
                    postId = postId,
                    placeParsingStatus = PlaceParsingStatus.PENDING,
                    failureReason = "No place candidate matched",
                    places = emptyList(),
                )
            },
        )

        val result = useCase(FindPostPlaceParsingUseCase.Query(7, 11))

        assertNull(result.failureReason)
    }
}
