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

    @Test
    fun `selects a unique rank zero candidate whose full name is explicit in the query`() {
        val clue = PlaceClue("바버샵", "서촌", listOf("바버샵 서촌"))
        val candidates = listOf(
            candidate("바버샵", "서울 종로구 자하문로12길 17", setOf("NAVER"), clue.queries),
            candidate("바버샵 베스티스", "서울 종로구 필운대로 12", setOf("NAVER"), emptyList()).copy(
                matchedQueries = clue.queries,
                matchedQueryRanks = mapOf(clue.queries.single() to 1),
            ),
        )

        val result = CandidateResolutionPolicy().evaluate(CandidateResolutionPolicy.Context(clue, candidates)).result

        assertEquals("바버샵", result.selection?.place?.name)
        assertEquals("explicit_query_name", result.selection?.method)
    }

    @Test
    fun `does not select when multiple rank zero candidate names are explicit`() {
        val query = "바버샵 서촌점 바버샵"
        val clue = PlaceClue("서촌 헤어", "서촌", listOf(query))
        val candidates = listOf(
            candidate("바버샵", "서울 종로구 자하문로12길 17", setOf("NAVER"), listOf(query)),
            candidate("바버샵 서촌점", "서울 종로구 필운대로 12", setOf("NAVER"), listOf(query)),
        )

        val result = CandidateResolutionPolicy().evaluate(CandidateResolutionPolicy.Context(clue, candidates)).result

        assertNull(result.selection)
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
