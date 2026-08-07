package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceTag
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class StorePlaceTagsUseCaseTest {
    @Test
    fun `extracts distinct grounded tags and stores at most four`() {
        var stored = emptyList<InferredPlaceTag>()
        val inferred = listOf(
            tag(PlaceTag.QUIET),
            tag(PlaceTag.QUIET),
            tag(PlaceTag.SOLO_DINING),
            tag(PlaceTag.NEAT),
            tag(PlaceTag.FRIENDLY),
            tag(PlaceTag.COZY),
        )
        val useCase = StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort {
                PlaceTagSource("조용하고 혼밥하기 좋아요", listOf("정갈한"), listOf("https://image.test/1"))
            },
            extractor = PlaceTagExtractor { request ->
                assertEquals("조용하고 혼밥하기 좋아요", request.body)
                inferred
            },
            updatePort = PlaceTagUpdatePort { postId, placeId, tags ->
                assertEquals(11, postId)
                assertEquals(17, placeId)
                stored = tags
            },
        )

        useCase(PlaceTagsRequestedEvent(11, 17, candidate()))

        assertEquals(
            listOf(PlaceTag.QUIET, PlaceTag.SOLO_DINING, PlaceTag.NEAT, PlaceTag.FRIENDLY),
            stored.map(InferredPlaceTag::tag),
        )
    }

    private fun tag(tag: PlaceTag): InferredPlaceTag = InferredPlaceTag(
        tag = tag,
        confidence = 0.9,
        evidenceSource = PlaceTagEvidenceSource.BODY,
        evidenceText = "근거",
    )

    private fun candidate(): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1",
        name = "누크 식당",
        address = "서울",
        latitude = BigDecimal("37.0"),
        longitude = BigDecimal("127.0"),
        category = "음식점",
        phoneNumber = null,
        providerUrl = null,
    )
}
