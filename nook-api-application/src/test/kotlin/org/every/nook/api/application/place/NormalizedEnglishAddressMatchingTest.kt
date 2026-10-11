package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ParsingRuleOutcome
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NormalizedEnglishAddressMatchingTest {
    @Test
    fun `English names search English first and retain normalized address for Korean fallback`() {
        val clue = clue().copy(queries = listOf("Cafe Onion Anguk Seoul", "Cafe Onion Anguk Gyedong-gil", "어니언 안국"))

        assertEquals(
            listOf(ENGLISH_ADDRESS, "Cafe Onion Anguk", "Cafe Onion Anguk Seoul", "Cafe Onion Anguk Gyedong-gil"),
            clue.searchPlan().primary,
        )
        assertEquals(listOf(KOREAN_ADDRESS, "어니언 안국"), clue.searchPlan().fallback)
        assertEquals(ENGLISH_ADDRESS, clue.addressHint)
    }

    @Test
    fun `normalized address alone cannot bypass business identity even after model selection`() {
        val clue = clue()
        val place = candidate()
        val candidates = listOf(PlaceCandidateSelector.Candidate(place, listOf(KOREAN_ADDRESS)))
        val policy = CandidateResolutionPolicy()

        assertNull(policy.evaluate(CandidateResolutionPolicy.Context(clue, candidates)).result.selection)
        assertEquals(
            ParsingRuleOutcome.FAILED,
            policy.evaluateSelectionValidation(clue, CandidateSelection(place, "openai"), candidates, null).outcome,
        )
        assertEquals(
            ParsingRuleOutcome.FAILED,
            policy.evaluateSelectionValidation(
                clue,
                CandidateSelection(place, "grounded_match"),
                candidates,
                null,
            ).outcome,
        )
    }

    @Test
    fun `normalized address and explicit Korean name alias establish the same business`() {
        val clue = clue().copy(queries = listOf("카페 어니언 안국 종로구 계동길 5"))
        val place = candidate()
        val candidates = listOf(PlaceCandidateSelector.Candidate(place, listOf(KOREAN_ADDRESS)))
        val policy = CandidateResolutionPolicy()

        assertEquals(
            place,
            policy.evaluate(CandidateResolutionPolicy.Context(clue, candidates)).result.selection?.place,
        )
        assertEquals(
            ParsingRuleOutcome.PASSED,
            policy.evaluateSelectionValidation(clue, CandidateSelection(place, "openai"), candidates, null).outcome,
        )
        assertFalse(clue.hasNormalizedAddressSelectionGrounding(place.copy(name = "다른 가게"), listOf(KOREAN_ADDRESS)))
    }

    @Test
    fun `rejects candidates with conflicting road building city or district even after model selection`() {
        val clue = clue()
        val policy = CandidateResolutionPolicy()
        listOf(
            "서울 종로구 계동길 50",
            "서울 종로구 삼청로 5",
            "부산 종로구 계동길 5",
            "서울 마포구 계동길 5",
        ).forEach { address ->
            val place = candidate(address)
            val candidates = listOf(PlaceCandidateSelector.Candidate(place, listOf(KOREAN_ADDRESS)))
            assertEquals(emptyList(), candidates.compatibleWith(clue), address)
            assertEquals(
                ParsingRuleOutcome.FAILED,
                policy.evaluateSelectionValidation(clue, CandidateSelection(place, "openai"), candidates, null).outcome,
                address,
            )
        }
    }

    @Test
    fun `discarded normalization cannot introduce a new grounding path`() {
        listOf("서울 종로구 계동길 50", "부산 해운대구 계동길 5").forEach { normalized ->
            val clue = clue().copy(normalizedAddressHint = normalized)

            assertNull(clue.validatedNormalizedAddress())
            assertFalse(clue.hasNormalizedAddressSelectionGrounding(candidate(), listOf(normalized)))
            assertEquals(ENGLISH_ADDRESS, clue.searchQueries().first())
            assertFalse(clue.copy(name = "어니언 안국").isSupportedBy(candidate(), listOf("어니언 안국")))
        }
    }

    @Test
    fun `normalization must preserve floor and room details and candidates must not conflict`() {
        val clue = clue().copy(
            addressHint = "$ENGLISH_ADDRESS, 2nd floor, Room 201",
            normalizedAddressHint = "$KOREAN_ADDRESS 2층 201호",
        )

        assertEquals(clue.normalizedAddressHint, clue.validatedNormalizedAddress())
        assertTrue(clue.isCompatibleWithNormalizedAddress(candidate("$KOREAN_ADDRESS 2층 201호")))
        assertFalse(clue.isCompatibleWithNormalizedAddress(candidate("$KOREAN_ADDRESS 3층 201호")))
        assertFalse(clue.isCompatibleWithNormalizedAddress(candidate("$KOREAN_ADDRESS 2층 202호")))
        listOf(KOREAN_ADDRESS, "$KOREAN_ADDRESS 3층 201호", "$KOREAN_ADDRESS 2층 202호").forEach {
            assertNull(clue.copy(normalizedAddressHint = it).validatedNormalizedAddress())
        }
    }

    @Test
    fun `supports basement and hyphenated building numbers without dropping details`() {
        val clue = clue().copy(
            addressHint = "99-9 Yanghwa-ro, Seoul, B1, Suite 201",
            normalizedAddressHint = "서울 마포구 양화로 99-9 지하 1층 201호",
        )

        assertEquals(clue.normalizedAddressHint, clue.validatedNormalizedAddress())
        assertNull(clue.copy(normalizedAddressHint = "서울 마포구 양화로 99 지하 1층 201호").validatedNormalizedAddress())
    }

    @Test
    fun `image grounding needs the original address and a search with the normalized address`() {
        val clue = clue().copy(
            queries = listOf("어니언 안국"),
            evidence = listOf(PlaceClueEvidence(1, "Cafe Onion Anguk $ENGLISH_ADDRESS")),
        )

        assertTrue(clue.hasNormalizedAddressSelectionGrounding(candidate(), listOf(KOREAN_ADDRESS)))
        assertTrue(clue.hasNormalizedAddressSelectionGrounding(candidate(), listOf("어니언 안국")))
        assertFalse(clue.hasNormalizedAddressSelectionGrounding(candidate(), listOf("Seoul Onion")))
        assertFalse(
            clue.copy(evidence = listOf(PlaceClueEvidence(1, "Cafe Onion Anguk")))
                .hasNormalizedAddressSelectionGrounding(candidate(), listOf(KOREAN_ADDRESS)),
        )
    }

    @Test
    fun `Korean and unsupported addresses do not activate English normalization`() {
        assertNull(clue().copy(addressHint = KOREAN_ADDRESS).validatedNormalizedAddress())
        assertNull(clue().copy(addressHint = "5 Main Street, London").validatedNormalizedAddress())
        assertNull(clue().copy(addressHint = null).validatedNormalizedAddress())
    }

    @Test
    fun `merging clues keeps the original address and its normalization together`() {
        val merged = listOf(clue().copy(addressHint = null, normalizedAddressHint = null), clue()).mergePlaceClues()

        assertEquals(ENGLISH_ADDRESS, merged.addressHint)
        assertEquals(KOREAN_ADDRESS, merged.normalizedAddressHint)
    }

    private fun clue() = PlaceClue(
        name = "Cafe Onion Anguk",
        region = "Seoul",
        queries = listOf("Cafe Onion Anguk"),
        addressHint = ENGLISH_ADDRESS,
        normalizedAddressHint = KOREAN_ADDRESS,
    )

    private fun candidate(address: String = KOREAN_ADDRESS) = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "853028118",
        name = "어니언 안국",
        address = address,
        latitude = BigDecimal("37.5778"),
        longitude = BigDecimal("126.9860"),
        category = "카페",
        phoneNumber = null,
        providerUrl = null,
    )

    private companion object {
        const val ENGLISH_ADDRESS = "5 Gyedong-gil, Jongno-gu, Seoul"
        const val KOREAN_ADDRESS = "서울 종로구 계동길 5"
    }
}
