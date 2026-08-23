package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceSearchEvidenceRecoveryTest {
    @Test
    fun `recovers Tune when two contextual queries and providers agree`() {
        val clue = PlaceClue("Tune", "성수동", listOf("Tune", "성수동 Tune", "Tune 서울 성수"))
        val selected = searchEvidenceCandidate(
            clue,
            listOf(candidate("튠", "서울 성동구 연무장길 33", setOf("KAKAO", "NAVER"), clue.queries.drop(1))),
        )

        assertEquals("튠", selected?.place?.name)
    }

    @Test
    fun `recovers Minus when menu query and regional query agree despite address neighborhood mismatch`() {
        val clue = PlaceClue("마이너스", "서촌", listOf("마이너스", "서촌 마이너스", "마이너스 타코 맛집"))
        val selected = searchEvidenceCandidate(
            clue,
            listOf(candidate("마이너스", "서울 종로구 돈화문로9길 6", setOf("KAKAO", "NAVER"), clue.queries.drop(1))),
        )

        assertEquals("서울 종로구 돈화문로9길 6", selected?.place?.address)
    }

    @Test
    fun `keeps same-name branches unresolved`() {
        val clue = PlaceClue("발발 빈티지", "성수동", listOf("발발 빈티지", "성수동 발발 빈티지", "발발 빈티지 서울 성수"))
        val candidates = listOf(
            candidate("발발 빈티지", "서울 성동구 성수이로 1", setOf("KAKAO", "NAVER"), clue.queries.drop(1)),
            candidate("발발 빈티지", "서울 성동구 연무장길 2", setOf("KAKAO"), listOf(clue.queries[1])),
        )

        assertNull(searchEvidenceCandidate(clue, candidates))
    }

    private fun candidate(name: String, address: String, providers: Set<String>, topQueries: List<String>) =
        PlaceCandidateSelector.Candidate(
            place = PlaceCandidate(
                provider = providers.first(),
                externalPlaceId = name + address,
                name = name,
                address = address,
                latitude = BigDecimal("37.0"),
                longitude = BigDecimal("127.0"),
                category = null,
                phoneNumber = null,
                providerUrl = null,
            ),
            matchedQueries = topQueries,
            matchedQueryRanks = topQueries.associateWith { 0 },
            supportingProviders = providers,
        )
}
