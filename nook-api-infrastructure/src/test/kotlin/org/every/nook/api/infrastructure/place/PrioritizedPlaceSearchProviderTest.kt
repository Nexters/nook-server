package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PrioritizedPlaceSearchProviderTest {
    @Test
    fun `skips Naver when Kakao name match is confident`() {
        var naverCalled = false
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider { listOf(candidate("KAKAO", "누크 카페")) },
            naver = PlaceSearchProvider {
                naverCalled = true
                emptyList()
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("누크 카페"))

        assertEquals("KAKAO", result.single().provider)
        assertEquals(false, naverCalled)
    }

    @Test
    fun `uses Naver to validate but keeps Kakao as the selected provider`() {
        var naverCalled = false
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider { listOf(candidate("KAKAO", "누크 용산점")) },
            naver = PlaceSearchProvider {
                naverCalled = true
                listOf(candidate("NAVER", "누크 용산"))
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("누크 카페"))

        assertEquals("KAKAO", result.first().provider)
        assertEquals(true, naverCalled)
    }

    @Test
    fun `includes exact Naver candidate when Kakao confidence is low`() {
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider {
                listOf(candidate("KAKAO", "어니언컴퍼니 안국점", address = "서울 종로구 계동길 5"))
            },
            naver = PlaceSearchProvider {
                listOf(candidate("NAVER", "어니언 안국", address = "서울 종로구 계동길 5"))
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("어니언 안국"))

        assertEquals(listOf("KAKAO", "NAVER"), result.map(PlaceCandidate::provider))
        assertEquals("어니언 안국", result.single { it.provider == "NAVER" }.name)
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
    fun `prefers Kakao candidate in same region over different region with same name`() {
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider {
                listOf(
                    candidate("KAKAO", "보니스피자", address = "부산 해운대구 달맞이길 10"),
                    candidate("KAKAO", "보니스피자", address = "서울 용산구 신흥로3길 2"),
                )
            },
            naver = PlaceSearchProvider { emptyList() },
        )

        val result = provider.search(PlaceSearchProvider.Request("보니스피자 용산구 신흥로3길"))

        assertEquals("서울 용산구 신흥로3길 2", result.first().address)
    }

    @Test
    fun `does not boost Kakao candidate when Naver match is in different region`() {
        val provider = PrioritizedPlaceSearchProvider(
            kakao = PlaceSearchProvider {
                listOf(
                    candidate("KAKAO", "모로코코", address = "서울 용산구 신흥로 34"),
                    candidate("KAKAO", "모로코코", address = "부산 수영구 광안해변로 1"),
                )
            },
            naver = PlaceSearchProvider {
                listOf(candidate("NAVER", "모로코코", address = "부산 수영구 광안해변로 1"))
            },
        )

        val result = provider.search(PlaceSearchProvider.Request("모로코코 용산구 카페"))

        assertEquals("서울 용산구 신흥로 34", result.first().address)
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
