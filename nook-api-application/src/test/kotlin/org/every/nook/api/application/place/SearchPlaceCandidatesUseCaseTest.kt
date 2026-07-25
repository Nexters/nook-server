package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchPlaceCandidatesUseCaseTest {
    @Test
    fun `searches distinct queries and removes duplicate provider places`() {
        val requests = mutableListOf<PlaceSearchProvider.Request>()
        val candidate = candidate("1")
        val useCase = SearchPlaceCandidatesUseCase { request ->
            requests += request
            listOf(candidate)
        }

        val result = useCase(
            SearchPlaceCandidatesUseCase.Command(
                queries = listOf(" Nook Cafe ", "Nook Cafe", "성수 카페"),
                longitude = BigDecimal("127.1"),
                latitude = BigDecimal("37.1"),
                radius = 1000,
            ),
        )

        assertEquals(listOf("Nook Cafe", "성수 카페"), requests.map { it.query })
        assertEquals(1, result.size)
    }

    @Test
    fun `empty provider result is returned as an empty candidate list`() {
        val useCase = SearchPlaceCandidatesUseCase { emptyList() }

        val result = useCase(SearchPlaceCandidatesUseCase.Command(queries = listOf("없는 장소")))

        assertEquals(emptyList(), result)
    }

    @Test
    fun `partial center coordinates are rejected`() {
        val useCase = SearchPlaceCandidatesUseCase { error("provider must not be called") }

        assertFailsWith<InvalidPlaceSearchRequestException> {
            useCase(
                SearchPlaceCandidatesUseCase.Command(
                    queries = listOf("Nook Cafe"),
                    longitude = BigDecimal("127.1"),
                ),
            )
        }
    }

    @Test
    fun `radius without center coordinates is rejected`() {
        val useCase = SearchPlaceCandidatesUseCase { error("provider must not be called") }

        assertFailsWith<InvalidPlaceSearchRequestException> {
            useCase(SearchPlaceCandidatesUseCase.Command(queries = listOf("Nook Cafe"), radius = 1000))
        }
    }

    private fun candidate(id: String): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = id,
        name = "Nook Cafe",
        address = "서울 성동구",
        latitude = BigDecimal("37.1"),
        longitude = BigDecimal("127.1"),
        category = "음식점 > 카페",
        phoneNumber = null,
        providerUrl = null,
    )
}
