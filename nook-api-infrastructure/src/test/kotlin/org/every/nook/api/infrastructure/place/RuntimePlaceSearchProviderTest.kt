package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimePlaceSearchProviderTest {
    @Test
    fun `uses configured provider and stops after a non-empty result`() {
        val calls = mutableListOf<String>()
        val provider = RuntimePlaceSearchProvider(
            providers = mapOf(
                PlaceParsingProviderType.APIFY_NAVER to PlaceSearchProvider {
                    calls += "APIFY_NAVER"
                    listOf(place())
                },
                PlaceParsingProviderType.LEGACY to PlaceSearchProvider {
                    calls += "LEGACY"
                    listOf(place())
                },
            ),
            configurationReader = { "APIFY_NAVER,LEGACY" },
        )

        assertEquals(1, provider.search(PlaceSearchProvider.Request("누크 카페")).size)
        assertEquals(listOf("APIFY_NAVER"), calls)
    }

    @Test
    fun `falls back to legacy when Apify is empty`() {
        val calls = mutableListOf<String>()
        val provider = RuntimePlaceSearchProvider(
            providers = mapOf(
                PlaceParsingProviderType.APIFY_NAVER to PlaceSearchProvider {
                    calls += "APIFY_NAVER"
                    emptyList()
                },
                PlaceParsingProviderType.LEGACY to PlaceSearchProvider {
                    calls += "LEGACY"
                    listOf(place())
                },
            ),
            configurationReader = { "APIFY_NAVER,LEGACY" },
        )

        assertEquals(1, provider.search(PlaceSearchProvider.Request("누크 카페")).size)
        assertEquals(listOf("APIFY_NAVER", "LEGACY"), calls)
    }

    private fun place() = PlaceCandidate(
        provider = "NAVER",
        externalPlaceId = "123",
        name = "누크 카페",
        address = "서울 강남구",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        category = "카페",
        phoneNumber = null,
        providerUrl = null,
    )
}
