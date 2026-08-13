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

    private fun candidate(provider: String, name: String) = PlaceCandidate(
        provider = provider,
        externalPlaceId = "$provider-$name",
        name = name,
        address = "서울 용산구 한강대로 1",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        category = null,
        phoneNumber = null,
        providerUrl = null,
    )
}
