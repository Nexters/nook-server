package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceClueLanguageSearchTest {
    @Test
    fun `Korean name with English address uses four Korean queries then three English queries`() {
        val clue = PlaceClue(
            name = "어니언 안국",
            region = null,
            queries = listOf("어니언 안국 서울", "어니언 안국 종로", "어니언 안국 계동", "Onion Anguk", "Onion Seoul", "Onion Cafe"),
            addressHint = "5 Gyedong-gil, Jongno-gu, Seoul",
        )
        val plan = clue.searchPlan()

        assertEquals(4, plan.primary.size)
        assertTrue(plan.primary.all { it.any { char -> char in '가'..'힣' } })
        assertEquals(3, plan.fallback.size)
        assertEquals(clue.addressHint, plan.fallback.first())
        assertTrue(plan.fallback.none { it.any { char -> char in '가'..'힣' } })
    }

    @Test
    fun `English name uses four English queries then three Korean queries without duplicate retries`() {
        val plan = bilingualClue().searchPlan()

        assertEquals(listOf("Onion", "Onion Anguk", "Onion Seoul", "Onion Cafe"), plan.primary)
        assertEquals(listOf("어니언 안국", "어니언 서울", "어니언 카페"), plan.fallback)
        assertEquals(7, (plan.primary + plan.fallback).distinct().size)
    }

    @Test
    fun `a resolved primary language does not search the other language`() {
        val attempts = mutableListOf<List<String>>()
        val selected = candidate()
        val result = bilingualClue().resolveWithLanguageFallback { queries ->
            attempts += queries
            selected
        }

        assertEquals(selected, result)
        assertEquals(1, attempts.size)
        assertEquals(4, attempts.single().size)
    }

    @Test
    fun `resolution failure switches language once even when first search has ungrounded candidates`() {
        val attempts = mutableListOf<List<String>>()
        val selected = candidate()
        val result = bilingualClue().resolveWithLanguageFallback { queries ->
            attempts += queries
            if (attempts.size == 1) failResolution("Selected place is not grounded in image evidence")
            selected
        }

        assertEquals(selected, result)
        assertEquals(listOf(4, 3), attempts.map(List<String>::size))
        assertEquals(bilingualClue().searchPlan().fallback, attempts.last())
    }

    @Test
    fun `failure in both languages stops after seven queries`() {
        val attempts = mutableListOf<List<String>>()
        val failure = assertFailsWith<PlaceResolutionException> {
            bilingualClue().resolveWithLanguageFallback { queries ->
                attempts += queries
                failResolution("failure-${attempts.size}")
            }
        }

        assertEquals("failure-2", failure.message)
        assertEquals(7, attempts.sumOf(List<String>::size))
    }

    @Test
    fun `provider failure does not trigger language fallback`() {
        var attempts = 0
        assertFailsWith<PlaceSearchProviderException> {
            bilingualClue().resolveWithLanguageFallback {
                attempts += 1
                throw PlaceSearchProviderException()
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `missing translated queries keeps the original failure without another attempt`() {
        var attempts = 0
        assertFailsWith<PlaceResolutionException> {
            PlaceClue("Onion", null, listOf("Onion")).resolveWithLanguageFallback {
                attempts += 1
                failResolution("No place candidate found")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `a strict name match cannot stop search before final address grounding passes`() {
        val clue = PlaceClue(
            name = "Onion",
            region = null,
            queries = listOf("Onion"),
            addressHint = "5 Gyedong-gil, Jongno-gu, Seoul",
            normalizedAddressHint = "서울 종로구 계동길 50",
        )
        val candidates = listOf(PlaceCandidateSelector.Candidate(candidate().copy(name = "Onion"), listOf("Onion")))

        assertEquals(1, strictMatches(clue, candidates).size)
        assertFalse(candidates.hasValidatedStrictMatch(clue))
    }

    private fun bilingualClue() = PlaceClue(
        name = "Onion",
        region = null,
        queries = listOf("Onion Anguk", "Onion Seoul", "Onion Cafe", "Onion Extra", "어니언 안국", "어니언 서울", "어니언 카페"),
    )

    private fun candidate() = PlaceCandidate(
        "KAKAO", "1", "어니언 안국", "서울 종로구 계동길 5",
        BigDecimal("37.5"), BigDecimal("127.0"), null, null, null,
    )
}
