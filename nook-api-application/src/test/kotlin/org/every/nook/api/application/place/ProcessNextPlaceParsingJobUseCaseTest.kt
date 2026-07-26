package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessNextPlaceParsingJobUseCaseTest {
    @Test
    fun `resolves and completes multiple place clues in order`() {
        val port = FakeJobPort()
        val extractor = PlaceClueExtractor {
            listOf(
                PlaceClue("원동미나리삼겹살", "용산구", listOf("용산 원동미나리삼겹살")),
                PlaceClue("서울역", null, listOf("서울역")),
            )
        }
        val provider = PlaceSearchProvider { request ->
            when (request.query) {
                "용산 원동미나리삼겹살" -> listOf(candidate("1", "원동미나리삼겹살", "서울 용산구 한강대로"))
                else -> listOf(candidate("2", "서울역", "서울 용산구 한강대로"))
            }
        }
        val useCase = ProcessNextPlaceParsingJobUseCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase(provider),
        )

        assertTrue(useCase())
        assertEquals(listOf("1", "2"), port.completed.map { it.externalPlaceId })
        assertEquals(null, port.failedReason)
    }

    @Test
    fun `completes without places when no clue is grounded`() {
        val port = FakeJobPort()
        val useCase = ProcessNextPlaceParsingJobUseCase(
            port,
            PlaceClueExtractor { emptyList() },
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertTrue(useCase())
        assertTrue(port.completed.isEmpty())
    }

    @Test
    fun `fails the entire job when a place is ambiguous`() {
        val port = FakeJobPort()
        val useCase = ProcessNextPlaceParsingJobUseCase(
            port,
            PlaceClueExtractor { listOf(PlaceClue("카페", null, listOf("카페"))) },
            SearchPlaceCandidatesUseCase {
                listOf(candidate("1", "카페", "서울"), candidate("2", "카페", "부산"))
            },
        )

        assertTrue(useCase())
        assertTrue(port.completed.isEmpty())
        assertTrue(requireNotNull(port.failedReason).contains("uniquely"))
    }

    private class FakeJobPort : PlaceParsingJobPort {
        var completed = emptyList<PlaceCandidate>()
        var failedReason: String? = null

        override fun claimNext(): ClaimedPlaceParsingJob = ClaimedPlaceParsingJob(1, "본문", emptyList(), null)

        override fun complete(postId: Long, places: List<PlaceCandidate>) {
            completed = places
        }

        override fun fail(postId: Long, reason: String) {
            failedReason = reason
        }
    }

    private companion object {
        fun candidate(id: String, name: String, address: String) = PlaceCandidate(
            provider = "KAKAO",
            externalPlaceId = id,
            name = name,
            address = address,
            latitude = BigDecimal("37.0"),
            longitude = BigDecimal("127.0"),
            category = null,
            phoneNumber = null,
            providerUrl = null,
        )
    }
}
