package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuntimePlaceThumbnailProviderTest {
    @Test
    fun `uses configured providers in order and stops after photos`() {
        val calls = mutableListOf<String>()
        val provider = provider(
            value = "GOOGLE, APIFY_GOOGLE, FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.GOOGLE to recording(calls, "GOOGLE", null),
                PlaceThumbnailProviderType.APIFY_GOOGLE to recording(
                    calls,
                    "APIFY_GOOGLE",
                    PlaceSupplement(null, listOf("https://cdn.example/naver.jpg")),
                ),
                PlaceThumbnailProviderType.FIXED to recording(
                    calls,
                    "FIXED",
                    PlaceSupplement(null, listOf("https://cdn.example/fixed.jpg")),
                ),
            ),
        )

        val result = provider.fetch(REQUEST)

        assertEquals(listOf("GOOGLE", "APIFY_GOOGLE"), calls)
        assertEquals(listOf("https://cdn.example/naver.jpg"), result?.photoUrls)
    }

    @Test
    fun `keeps supplement metadata while falling back for photos`() {
        val hours = PlaceOpeningHours("Asia/Seoul")
        val provider = provider(
            value = "GOOGLE,FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.GOOGLE to PlaceThumbnailProvider {
                    PlaceSupplement(hours, emptyList(), googlePlaceId = "google-id")
                },
                PlaceThumbnailProviderType.FIXED to PlaceThumbnailProvider {
                    PlaceSupplement(null, listOf("https://cdn.example/fixed.jpg"))
                },
            ),
        )

        val result = provider.fetch(REQUEST)

        assertEquals(hours, result?.openingHours)
        assertEquals("google-id", result?.googlePlaceId)
        assertEquals(listOf("https://cdn.example/fixed.jpg"), result?.photoUrls)
    }

    @Test
    fun `uses legacy chain when runtime value is missing or invalid`() {
        listOf(null, "UNKNOWN").forEach { value ->
            val provider = provider(
                value = value,
                delegates = mapOf(PlaceThumbnailProviderType.POST_MEDIA to PlaceThumbnailProvider { null }),
            )

            assertNull(provider.fetch(REQUEST))
        }
    }

    @Test
    fun `continues with next provider when a provider fails`() {
        val calls = mutableListOf<String>()
        val provider = provider(
            value = "APIFY_GOOGLE,FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.APIFY_GOOGLE to PlaceThumbnailProvider {
                    calls += "APIFY_GOOGLE"
                    error("provider failed")
                },
                PlaceThumbnailProviderType.FIXED to recording(
                    calls,
                    "FIXED",
                    PlaceSupplement(null, listOf("https://cdn.example/fixed.jpg")),
                ),
            ),
        )

        val result = provider.fetch(REQUEST)

        assertEquals(listOf("APIFY_GOOGLE", "FIXED"), calls)
        assertEquals(listOf("https://cdn.example/fixed.jpg"), result?.photoUrls)
    }

    @Test
    fun `disabled stops the remaining chain`() {
        val calls = mutableListOf<String>()
        val provider = provider(
            value = "DISABLED,FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.FIXED to recording(
                    calls,
                    "FIXED",
                    PlaceSupplement(null, listOf("https://cdn.example/fixed.jpg")),
                ),
            ),
        )

        assertNull(provider.fetch(REQUEST))
        assertEquals(emptyList(), calls)
    }

    private fun provider(
        value: String?,
        delegates: Map<PlaceThumbnailProviderType, PlaceThumbnailProvider>,
    ): RuntimePlaceThumbnailProvider = RuntimePlaceThumbnailProvider(
        providers = delegates,
        configurationReader = RuntimeConfigurationReader { value },
        legacyChain = listOf(PlaceThumbnailProviderType.POST_MEDIA),
    )

    private fun recording(calls: MutableList<String>, name: String, result: PlaceSupplement?): PlaceThumbnailProvider =
        PlaceThumbnailProvider {
            calls += name
            result
        }

    private companion object {
        val REQUEST = PlaceThumbnailProvider.Request(
            place = PlaceCandidate(
                provider = "NAVER",
                externalPlaceId = "place-id",
                name = "테스트 카페",
                address = "서울 강남구 테헤란로 1",
                latitude = BigDecimal("37.5000"),
                longitude = BigDecimal("127.0000"),
                category = null,
                phoneNumber = null,
                providerUrl = null,
            ),
        )
    }
}
