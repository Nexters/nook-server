package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceTag
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StorePlaceTagsUseCaseTest {
    @Test
    fun `stores only confident candidate tags with at most two per category`() {
        var stored = emptyList<InferredPlaceTag>()
        val inferred = listOf(
            tag(PlaceTag.AESTHETIC, 0.91),
            tag(PlaceTag.COZY, 0.90),
            tag(PlaceTag.QUIET, 0.89),
            tag(PlaceTag.DATE, 0.88),
            tag(PlaceTag.GOOD_VALUE, 0.64),
            tag(PlaceTag.PARKING, 0.87),
        )
        val useCase = useCase(inferred) { stored = it }

        useCase(event(target(17, candidate())))

        assertEquals(
            listOf(PlaceTag.AESTHETIC, PlaceTag.COZY, PlaceTag.DATE, PlaceTag.PARKING),
            stored.map(InferredPlaceTag::tag),
        )
    }

    @Test
    fun `extracts every place in a post with one AI request and isolated body sections`() {
        var extractorCallCount = 0
        var captured: PlaceTagExtractor.Request? = null
        val stored = mutableMapOf<Long, List<InferredPlaceTag>>()
        val source = PlaceTagSource(
            body = """
                누크 식당
                조용하고 데이트하기 좋아요
                다른 카페
                북적이는 핫플이에요
            """.trimIndent(),
            hashtags = listOf("핫플"),
        )
        val useCase = StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort { source },
            extractor = PlaceTagExtractor { request ->
                extractorCallCount += 1
                captured = request
                listOf(
                    PlaceTagExtractor.Result(0, listOf(tag(PlaceTag.QUIET), tag(PlaceTag.DATE))),
                    PlaceTagExtractor.Result(1, listOf(tag(PlaceTag.CROWDED), tag(PlaceTag.HOT_PLACE))),
                )
            },
            updatePort = PlaceTagUpdatePort { _, placeId, tags -> stored[placeId] = tags },
        )

        useCase(
            event(
                target(17, candidate(name = "누크 식당")),
                target(18, candidate(name = "다른 카페")),
            ),
        )

        assertEquals(1, extractorCallCount)
        val request = requireNotNull(captured)
        assertEquals(2, request.places.size)
        assertEquals("누크 식당\n조용하고 데이트하기 좋아요", request.places.first().body)
        assertEquals(listOf(PlaceTag.QUIET, PlaceTag.DATE), request.places.first().candidateTags)
        assertEquals("다른 카페\n북적이는 핫플이에요", request.places.last().body)
        assertEquals(listOf(PlaceTag.HOT_PLACE, PlaceTag.CROWDED), request.places.last().candidateTags)
        assertEquals(emptyList(), request.places.first().hashtags)
        assertEquals(listOf(PlaceTag.QUIET, PlaceTag.DATE), stored.getValue(17).map(InferredPlaceTag::tag))
        assertEquals(listOf(PlaceTag.CROWDED, PlaceTag.HOT_PLACE), stored.getValue(18).map(InferredPlaceTag::tag))
    }

    @Test
    fun `stores empty tags without calling AI when no catalog keyword matches`() {
        var extractorCalled = false
        var stored = listOf(tag(PlaceTag.COZY))
        val useCase = StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort {
                PlaceTagSource("오늘 방문했습니다", emptyList())
            },
            extractor = PlaceTagExtractor {
                extractorCalled = true
                emptyList()
            },
            updatePort = PlaceTagUpdatePort { _, _, tags -> stored = tags },
        )

        useCase(event(target(17, candidate())))

        assertFalse(extractorCalled)
        assertEquals(emptyList(), stored)
    }

    @Test
    fun `rejects an AI tag whose evidence says the source has no supporting text`() {
        var stored = listOf(tag(PlaceTag.DESSERT))
        val useCase = StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort { PlaceTagSource("디저트 관련 표현은 없어요", emptyList()) },
            extractor = PlaceTagExtractor { request ->
                listOf(
                    PlaceTagExtractor.Result(
                        request.places.single().placeIndex,
                        listOf(
                            tag(
                                PlaceTag.DESSERT,
                                evidenceText = "본문에 디저트 관련 표현은 없으므로 선택하지 않습니다.",
                            ),
                        ),
                    ),
                )
            },
            updatePort = PlaceTagUpdatePort { _, _, tags -> stored = tags },
        )

        useCase(event(target(17, candidate())))

        assertEquals(emptyList(), stored)
    }

    private fun useCase(inferred: List<InferredPlaceTag>, store: (List<InferredPlaceTag>) -> Unit) =
        StorePlaceTagsUseCase(
            sourcePort = PlaceTagSourcePort {
                PlaceTagSource(
                    "감성적이고 아늑하지만 조용하며 데이트하기 좋아요. 가성비가 좋고 주차 가능해요",
                    emptyList(),
                )
            },
            extractor = PlaceTagExtractor { request ->
                val input = request.places.single()
                assertEquals(
                    listOf(
                        PlaceTag.AESTHETIC,
                        PlaceTag.COZY,
                        PlaceTag.QUIET,
                        PlaceTag.DATE,
                        PlaceTag.GOOD_VALUE,
                        PlaceTag.PARKING,
                    ),
                    input.candidateTags,
                )
                listOf(PlaceTagExtractor.Result(input.placeIndex, inferred))
            },
            updatePort = PlaceTagUpdatePort { _, _, tags -> store(tags) },
        )

    private fun event(vararg places: PlaceTagsRequestedEvent.Place) = PlaceTagsRequestedEvent(11, places.toList())

    private fun target(placeId: Long, candidate: PlaceCandidate) = PlaceTagsRequestedEvent.Place(placeId, candidate)

    private fun tag(tag: PlaceTag, confidence: Double = 0.9, evidenceText: String = "근거") = InferredPlaceTag(
        tag = tag,
        confidence = confidence,
        evidenceSource = PlaceTagEvidenceSource.BODY,
        evidenceText = evidenceText,
    )

    private fun candidate(name: String = "누크 식당"): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = name,
        name = name,
        address = "서울",
        latitude = BigDecimal("37.0"),
        longitude = BigDecimal("127.0"),
        category = "음식점",
        phoneNumber = null,
        providerUrl = null,
    )
}
