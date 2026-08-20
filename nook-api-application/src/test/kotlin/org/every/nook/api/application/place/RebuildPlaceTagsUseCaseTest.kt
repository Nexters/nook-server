package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class RebuildPlaceTagsUseCaseTest {
    @Test
    fun `continues rebuilding after a target fails and reports the result`() {
        val events = listOf(event(1), event(2), event(3))
        val store = StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort { postId ->
                if (postId == 2L) error("failed source")
                PlaceTagSource("조용한 장소", emptyList())
            },
            extractor = PlaceTagExtractor { emptyList() },
            updatePort = PlaceTagUpdatePort { _, _, _ -> },
        )
        val useCase = RebuildPlaceTagsUseCase(PlaceTagBackfillPort { events }, store)

        val result = useCase()

        assertEquals(RebuildPlaceTagsUseCase.Result(succeeded = 2, failedPostIds = listOf(2)), result)
    }

    @Test
    fun `rebuilds only requested posts`() {
        val storedPostIds = mutableListOf<Long>()
        val events = listOf(event(1), event(2), event(3))
        val store = StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort { postId ->
                storedPostIds += postId
                PlaceTagSource("조용한 장소", emptyList())
            },
            extractor = PlaceTagExtractor { emptyList() },
            updatePort = PlaceTagUpdatePort { _, _, _ -> },
        )
        val useCase = RebuildPlaceTagsUseCase(PlaceTagBackfillPort { events }, store)

        val result = useCase(setOf(2, 3))

        assertEquals(listOf(2L, 3L), storedPostIds)
        assertEquals(RebuildPlaceTagsUseCase.Result(succeeded = 2, failedPostIds = emptyList()), result)
    }

    private fun event(postId: Long) = PlaceTagsRequestedEvent(
        postId = postId,
        places = listOf(
            PlaceTagsRequestedEvent.Place(
                placeId = postId,
                candidate = PlaceCandidate(
                    provider = "KAKAO",
                    externalPlaceId = postId.toString(),
                    name = "누크",
                    address = "서울",
                    latitude = BigDecimal("37.0"),
                    longitude = BigDecimal("127.0"),
                    category = null,
                    phoneNumber = null,
                    providerUrl = null,
                ),
            ),
        ),
    )
}
