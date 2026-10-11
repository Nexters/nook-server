package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ParsingRuleOutcome
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnglishPlaceMatchingTest {
    @Test
    fun `Korean aliases remain available for fallback after four English queries`() {
        val clue = PlaceClue("Cafe Onion Anguk", "Seoul", listOf("Cafe Onion Anguk 카페 어니언 안국 서울"))

        assertEquals(listOf("Cafe Onion Anguk", "Seoul Cafe", "Seoul Onion", "Seoul Anguk"), clue.searchPlan().primary)
        assertEquals(listOf("Cafe Onion Anguk 카페 어니언 안국 서울"), clue.searchPlan().fallback)
    }

    @Test
    fun `model selection still fails final grounding for English name and address without Korean alias`() {
        val clue = PlaceClue("Cafe Onion Anguk", null, listOf("Cafe Onion Anguk"), addressHint = ENGLISH_ADDRESS)
        val place = candidate("어니언 안국")
        val candidates = listOf(PlaceCandidateSelector.Candidate(place, listOf("Seoul Onion")))
        val policy = CandidateResolutionPolicy()

        assertEquals(
            ParsingRuleOutcome.FAILED,
            policy.evaluateSelectionValidation(clue, CandidateSelection(place, "openai"), candidates, null).outcome,
        )
        assertEquals(
            ParsingRuleOutcome.PASSED,
            policy.evaluateSelectionValidation(
                clue.copy(queries = clue.queries + "어니언 안국"),
                CandidateSelection(place, "openai"),
                candidates,
                null,
            ).outcome,
        )
    }

    @Test
    fun `English names retain source grounding and search spelling`() {
        val clue = PlaceClue("Cafe Onion Anguk", "Seoul", listOf("Cafe Onion Anguk"))

        assertTrue(clue.isGroundedIn("Visited Cafe Onion Anguk in Seoul", emptyList()))
        assertTrue("Cafe Onion Anguk" in clue.searchQueries())
        assertTrue(clue.isSupportedBy(candidate("Cafe Onion Anguk")))
        assertTrue(clue.isSupportedBy(candidate("어니언 안국")))
    }

    @Test
    fun `Korean names can be grounded through an explicit original English query`() {
        val clue = PlaceClue("어니언 안국", "Seoul", listOf("Cafe Onion Anguk", "어니언 안국"))

        assertTrue(clue.isGroundedIn("Visited Cafe Onion Anguk in Seoul", emptyList()))
        assertFalse(clue.copy(queries = listOf("어니언 안국")).isGroundedIn("Cafe Onion Anguk", emptyList()))
        assertTrue(clue.isGroundedIn("Cafe Onion Anguk (어니언 안국)", emptyList()))
    }

    @Test
    fun `English only name does not establish Korean candidate identity in image evidence`() {
        val clue = PlaceClue(
            "Cafe Onion Anguk",
            null,
            listOf("Cafe Onion Anguk"),
            evidence = listOf(PlaceClueEvidence(1, "Cafe Onion Anguk")),
        )

        assertFalse(clue.isSupportedBy(candidate("어니언 안국")))
        assertTrue(clue.copy(queries = clue.queries + "어니언 안국").isSupportedBy(candidate("어니언 안국")))
    }

    @Test
    fun `same Korean road address can ground a differently named English candidate`() {
        val clue = PlaceClue(
            "원형들",
            null,
            listOf("원형들"),
            evidence = listOf(PlaceClueEvidence(1, "서울 중구 창경궁로1길 38")),
        )

        assertTrue(clue.isSupportedBy(candidate("ONEHYUNG", "서울 중구 창경궁로1길 38")))
    }

    @Test
    fun `English address search is retained without Korean road translation`() {
        val clue = PlaceClue("어니언 안국", null, listOf("어니언 안국"), addressHint = ENGLISH_ADDRESS)

        assertEquals("어니언 안국", clue.searchPlan().primary.first())
        assertEquals(ENGLISH_ADDRESS, clue.searchPlan().fallback.first())
        assertEquals(emptySet(), PlaceAddressMatcher.addressKeys(ENGLISH_ADDRESS))
        assertEquals(listOf(ENGLISH_ADDRESS), PlaceAddressMatcher.searchVariants(ENGLISH_ADDRESS))
        assertFalse(clue.hasGroundedExactAddressSearch(candidate("어니언 안국"), listOf(ENGLISH_ADDRESS)))
        assertTrue(clue.isSupportedBy(candidate("어니언 안국"), listOf("어니언 안국")))
    }

    @Test
    fun `English name and address require a matching name or explicit alias for Korean candidates`() {
        val clue = PlaceClue("Cafe Onion Anguk", null, listOf("Cafe Onion Anguk"), addressHint = ENGLISH_ADDRESS)

        assertFalse(clue.isSupportedBy(candidate("어니언 안국"), listOf(ENGLISH_ADDRESS)))
        assertTrue(clue.copy(queries = listOf("어니언 안국")).isSupportedBy(candidate("어니언 안국")))
    }

    @Test
    fun `English address compatibility is permissive rather than proof of the same building`() {
        assertTrue(PlaceAddressMatcher.isCompatible(ENGLISH_ADDRESS, KOREAN_ADDRESS))
        assertTrue(PlaceAddressMatcher.isCompatible(ENGLISH_ADDRESS, "부산 해운대구 달맞이길 30"))
        assertFalse(PlaceAddressMatcher.isCompatible(KOREAN_ADDRESS, "서울 종로구 계동길 50"))
    }

    @Test
    fun `English address alone does not establish original business name grounding`() {
        val clue = PlaceClue("어니언 안국", null, listOf("어니언 안국"), addressHint = ENGLISH_ADDRESS)

        assertFalse(clue.isGroundedIn(ENGLISH_ADDRESS, emptyList()))
    }

    private fun candidate(name: String, address: String = KOREAN_ADDRESS) = PlaceCandidate(
        provider = "AUDIT",
        externalPlaceId = "1",
        name = name,
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
