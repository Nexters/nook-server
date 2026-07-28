package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.every.nook.api.application.place.PlaceSearchProviderTimeoutException
import java.math.BigDecimal
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompositePlaceSearchProviderTest {
    @Test
    fun `returns first non-empty success result`() {
        val provider = compositeProvider(
            namedProvider("SLOW", delayMillis = 100) { listOf(candidate("SLOW")) },
            namedProvider("FAST", delayMillis = 10) { listOf(candidate("FAST")) },
        )

        val result = provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))

        assertEquals("FAST", result.single().provider)
    }

    @Test
    fun `waits for another provider when first success is empty`() {
        val provider = compositeProvider(
            namedProvider("EMPTY", delayMillis = 10) { emptyList() },
            namedProvider("FILLED", delayMillis = 50) { listOf(candidate("FILLED")) },
        )

        val result = provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))

        assertEquals("FILLED", result.single().provider)
    }

    @Test
    fun `uses successful provider when another provider fails`() {
        val provider = compositeProvider(
            namedProvider("FAIL") { throw PlaceSearchProviderException() },
            namedProvider("OK") { listOf(candidate("OK")) },
        )

        val result = provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))

        assertEquals("OK", result.single().provider)
    }

    @Test
    fun `throws timeout when every provider times out`() {
        val provider = compositeProvider(
            namedProvider("TIMEOUT_1") { throw PlaceSearchProviderTimeoutException() },
            namedProvider("TIMEOUT_2") { throw PlaceSearchProviderTimeoutException() },
        )

        assertFailsWith<PlaceSearchProviderTimeoutException> {
            provider.search(PlaceSearchProvider.Request(query = "Nook Cafe"))
        }
    }

    private fun compositeProvider(
        vararg providers: CompositePlaceSearchProvider.NamedPlaceSearchProvider,
    ): CompositePlaceSearchProvider = CompositePlaceSearchProvider(
        providers = providers.toList(),
        executor = Executors.newFixedThreadPool(providers.size),
    )

    private fun namedProvider(
        name: String,
        delayMillis: Long = 0,
        result: () -> List<PlaceCandidate>,
    ): CompositePlaceSearchProvider.NamedPlaceSearchProvider = CompositePlaceSearchProvider.NamedPlaceSearchProvider(
        name = name,
        provider = PlaceSearchProvider {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis)
            }
            result()
        },
    )

    private fun candidate(provider: String): PlaceCandidate = PlaceCandidate(
        provider = provider,
        externalPlaceId = provider,
        name = "Nook Cafe",
        address = "서울 성동구 아차산로 1",
        latitude = BigDecimal("37.1"),
        longitude = BigDecimal("127.1"),
        category = null,
        phoneNumber = null,
        providerUrl = null,
    )
}
