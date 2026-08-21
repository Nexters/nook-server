package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PrioritizedPlaceSearchProviderTest {
    @Test
    fun `skips Kakao when Naver name match is confident`() {
        var kakaoCalled = false
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider {
                kakaoCalled = true
                emptyList()
            },
            naver = PlaceSearchProvider { listOf(candidate("NAVER", "누크 카페")) },
        )

        val result = provider.search(PlaceSearchProvider.Request("누크 카페"))

        assertEquals("NAVER", result.single().provider)
        assertEquals(false, kakaoCalled)
    }

    @Test
    fun `uses Kakao to validate but keeps Naver as the selected provider`() {
        var kakaoCalled = false
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider {
                kakaoCalled = true
                listOf(candidate("KAKAO", "누크 용산"))
            },
            naver = PlaceSearchProvider { listOf(candidate("NAVER", "누크 용산점")) },
        )

        val result = provider.search(PlaceSearchProvider.Request("누크 카페"))

        assertEquals("NAVER", result.first().provider)
        assertEquals(true, kakaoCalled)
    }

    @Test
    fun `includes exact Kakao candidate when Naver confidence is low`() {
        val provider = PrioritizedPlaceSearchProvider(
            naver = PlaceSearchProvider {
                listOf(candidate("NAVER", "어니언컴퍼니 안국점", address = "서울 종로구 계동길 5"))
            },
            kakao = PlaceSearchProvider {
                listOf(candidate("KAKAO", "어니언 안국", address = "서울 종로구 계동길 5"))
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("어니언 안국"))

        assertEquals(listOf("NAVER", "KAKAO"), result.map(PlaceCandidate::provider))
        assertEquals("어니언 안국", result.single { it.provider == "KAKAO" }.name)
    }

    @Test
    fun `removes duplicate candidates after merging Kakao and Naver results`() {
        val duplicate = candidate("SHARED", "누크 카페")
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider { listOf(duplicate) },
            naver = PlaceSearchProvider { listOf(duplicate) },
        )

        val result = provider.search(PlaceSearchProvider.Request("용산"))

        assertEquals(listOf(duplicate), result)
    }

    @Test
    fun `prefers Naver candidate in same region over different region with same name`() {
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider { emptyList() },
            naver = PlaceSearchProvider {
                listOf(
                    candidate("NAVER", "보니스피자", address = "부산 해운대구 달맞이길 10"),
                    candidate("NAVER", "보니스피자", address = "서울 용산구 신흥로3길 2"),
                )
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("보니스피자 용산구 신흥로3길"))

        assertEquals("서울 용산구 신흥로3길 2", result.first().address)
    }

    @Test
    fun `does not boost Naver candidate when Kakao match is in different region`() {
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider {
                listOf(candidate("KAKAO", "모로코코", address = "부산 수영구 광안해변로 1"))
            },
            naver = PlaceSearchProvider {
                listOf(
                    candidate("NAVER", "모로코코", address = "서울 용산구 신흥로 34"),
                    candidate("NAVER", "모로코코", address = "부산 수영구 광안해변로 1"),
                )
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("모로코코 용산구 카페"))

        assertEquals("서울 용산구 신흥로 34", result.first().address)
    }

    @Test
    fun `falls back to Kakao when Naver search fails`() {
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider { listOf(candidate("KAKAO", "누크 카페")) },
            naver = PlaceSearchProvider { error("Naver unavailable") },
        )

        val result = provider.search(PlaceSearchProvider.Request("누크 카페"))

        assertEquals("KAKAO", result.single().provider)
    }

    private fun candidate(provider: String, name: String, address: String = "서울 용산구 한강대로 1"): PlaceCandidate =
        PlaceCandidate(
            provider = provider,
            externalPlaceId = "$provider-$name",
            name = name,
            address = address,
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            category = null,
            phoneNumber = null,
            providerUrl = null,
        )
}
