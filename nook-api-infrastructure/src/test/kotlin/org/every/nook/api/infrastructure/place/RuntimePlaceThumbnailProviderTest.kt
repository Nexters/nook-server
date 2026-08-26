package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import java.math.BigDecimal
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuntimePlaceThumbnailProviderTest {
    @Test
    fun `uses configured providers in order and stops after photos`() {
        val calls = mutableListOf<String>()
        val provider = provider(
            value = "APIFY_NAVER_PLACE, APIFY_GOOGLE, FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.APIFY_NAVER_PLACE to recording(calls, "APIFY_NAVER_PLACE", null),
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

        assertEquals(listOf("APIFY_NAVER_PLACE", "APIFY_GOOGLE"), calls)
        assertEquals(listOf("https://cdn.example/naver.jpg"), result?.photoUrls)
    }

    @Test
    fun `keeps supplement metadata while falling back for photos`() {
        val hours = PlaceOpeningHours("Asia/Seoul")
        val provider = provider(
            value = "APIFY_GOOGLE,FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.APIFY_GOOGLE to PlaceThumbnailProvider {
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
    fun `routes from Apify Google to Apify Naver place without Google`() {
        val calls = mutableListOf<String>()
        val provider = provider(
            value = "APIFY_GOOGLE,APIFY_NAVER_PLACE,POST_MEDIA",
            delegates = mapOf(
                PlaceThumbnailProviderType.APIFY_GOOGLE to recording(calls, "APIFY_GOOGLE", null),
                PlaceThumbnailProviderType.APIFY_NAVER_PLACE to recording(
                    calls,
                    "APIFY_NAVER_PLACE",
                    PlaceSupplement(null, listOf("https://cdn.example/naver.jpg")),
                ),
                PlaceThumbnailProviderType.POST_MEDIA to recording(calls, "POST_MEDIA", null),
            ),
        )

        val result = provider.fetch(REQUEST)

        assertEquals(listOf("APIFY_GOOGLE", "APIFY_NAVER_PLACE"), calls)
        assertEquals(listOf("https://cdn.example/naver.jpg"), result?.photoUrls)
    }

    @Test
    fun `reports resolved places before invoking the next fallback provider`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstResolved = CountDownLatch(1)
        val first = REQUEST
        val second = REQUEST.copy(place = REQUEST.place.copy(externalPlaceId = "second-place"))
        val provider = provider(
            value = "APIFY_GOOGLE,APIFY_NAVER_PLACE",
            delegates = mapOf(
                PlaceThumbnailProviderType.APIFY_GOOGLE to PlaceThumbnailProvider { request ->
                    request.takeIf { it.place.externalPlaceId == "place-id" }
                        ?.let { PlaceSupplement(null, listOf("https://cdn.example/google.jpg")) }
                },
                PlaceThumbnailProviderType.APIFY_NAVER_PLACE to PlaceThumbnailProvider { request ->
                    check(firstResolved.await(1, TimeUnit.SECONDS))
                    events += "naver:${request.place.externalPlaceId}"
                    PlaceSupplement(null, listOf("https://cdn.example/naver.jpg"))
                },
            ),
        )

        provider.fetchAll(listOf(first, second)) { request, _ ->
            events += "resolved:${request.place.externalPlaceId}"
            if (request.place.externalPlaceId == "place-id") firstResolved.countDown()
        }

        assertEquals(
            listOf("resolved:place-id", "naver:second-place", "resolved:second-place"),
            events,
        )
    }

    @Test
    fun `runs at most six place chains concurrently and preserves result order`() {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val firstWave = CountDownLatch(6)
        val provider = provider(
            value = "FIXED",
            delegates = mapOf(
                PlaceThumbnailProviderType.FIXED to PlaceThumbnailProvider { request ->
                    val current = active.incrementAndGet()
                    maxActive.updateAndGet { previous -> maxOf(previous, current) }
                    firstWave.countDown()
                    check(firstWave.await(1, TimeUnit.SECONDS))
                    active.decrementAndGet()
                    PlaceSupplement(null, listOf("https://cdn.example/${request.place.externalPlaceId}.jpg"))
                },
            ),
        )
        val requests = (1..7).map { index ->
            REQUEST.copy(place = REQUEST.place.copy(externalPlaceId = "place-$index"))
        }

        val results = provider.fetchAll(requests)

        assertEquals(6, maxActive.get())
        assertEquals(
            (1..7).map { index -> listOf("https://cdn.example/place-$index.jpg") },
            results.map { it?.photoUrls },
        )
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
