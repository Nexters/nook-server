package org.every.nook.api.application.place

import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProcessPlaceParsingJobUseCaseTest {
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
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf("1", "2"), port.completed.map { it.externalPlaceId })
        assertNull(port.failedReason)
    }

    @Test
    fun `resolves a place clue with four search queries`() {
        val port = FakeJobPort()
        val queries = listOf("Lodge190", "롯지190", "롯지 190", "연희동 Lodge")
        val extractor = PlaceClueExtractor {
            listOf(PlaceClue("Lodge190", "연희동", queries))
        }
        val searchedQueries = mutableListOf<String>()
        val provider = PlaceSearchProvider { request ->
            searchedQueries += request.query
            if (request.query == "Lodge190") {
                listOf(candidate("1", "Lodge190", "서울 서대문구 연희동"))
            } else {
                emptyList()
            }
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(queries, searchedQueries)
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `completes with resolved places when some place clues do not match`() {
        val port = FakeJobPort()
        val extractor = PlaceClueExtractor {
            listOf(
                PlaceClue("첫 번째 장소", "서울", listOf("첫 번째 장소")),
                PlaceClue("매칭 실패 장소", "서울", listOf("매칭 실패 장소")),
                PlaceClue("두 번째 장소", "서울", listOf("두 번째 장소")),
            )
        }
        val provider = PlaceSearchProvider { request ->
            when (request.query) {
                "첫 번째 장소" -> listOf(candidate("1", "첫 번째 장소", "서울 중구"))
                "두 번째 장소" -> listOf(candidate("2", "두 번째 장소", "서울 종로구"))
                else -> emptyList()
            }
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf("1", "2"), port.completed.map { it.externalPlaceId })
        assertNull(port.nextAttemptAt)
        assertNull(port.failedReason)
    }

    @Test
    fun `retries when every place clue fails to match`() {
        val port = FakeJobPort(attempt = 2)
        val extractor = PlaceClueExtractor {
            listOf(PlaceClue("매칭 실패 장소", "서울", listOf("매칭 실패 장소")))
        }
        val useCase = useCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Retry>(useCase(1))
        assertEquals(NOW.plusSeconds(3), port.nextAttemptAt)
        assertEquals(emptyList(), port.completed)
    }

    @Test
    fun `retries the whole job when place search provider fails after a resolved clue`() {
        val port = FakeJobPort(attempt = 2)
        val extractor = PlaceClueExtractor {
            listOf(
                PlaceClue("정상 장소", "서울", listOf("정상 장소")),
                PlaceClue("검색 오류 장소", "서울", listOf("검색 오류 장소")),
            )
        }
        val provider = PlaceSearchProvider { request ->
            when (request.query) {
                "정상 장소" -> listOf(candidate("1", "정상 장소", "서울 중구"))
                else -> throw PlaceSearchProviderException()
            }
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Retry>(useCase(1))
        assertEquals(emptyList(), port.completed)
        assertNull(port.failedReason)
    }

    @Test
    fun `fails permanently when the final attempt extracts no place clue`() {
        val port = FakeJobPort(attempt = 4)
        val useCase = useCase(
            port,
            PlaceClueExtractor { emptyList() },
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Failed>(useCase(1))
        assertEquals("No place clue was extracted", port.failedReason)
    }

    @Test
    fun `schedules retries with configured backoff for the first three failed attempts`() {
        val port = FakeJobPort(attempt = 2)
        val useCase = useCase(
            port,
            PlaceClueExtractor { error("temporary provider failure") },
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        val result = assertIs<ProcessPlaceParsingJobUseCase.Result.Retry>(useCase(1))

        assertEquals(NOW.plusSeconds(3), result.nextAttemptAt)
        assertEquals(NOW.plusSeconds(3), port.nextAttemptAt)
        assertEquals("temporary provider failure", port.retryReason)
        assertNull(port.failedReason)
    }

    @Test
    fun `fails permanently after the initial attempt and three retries`() {
        val port = FakeJobPort(attempt = 4)
        val useCase = useCase(
            port,
            PlaceClueExtractor { error("permanent provider failure") },
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Failed>(useCase(1))
        assertEquals("permanent provider failure", port.failedReason)
        assertNull(port.nextAttemptAt)
    }

    private fun useCase(
        port: FakeJobPort,
        extractor: PlaceClueExtractor,
        search: SearchPlaceCandidatesUseCase,
    ): ProcessPlaceParsingJobUseCase = ProcessPlaceParsingJobUseCase(
        jobPort = port,
        clueExtractor = extractor,
        searchPlaceCandidates = search,
        retryBackoffs = RETRY_BACKOFFS,
        processingTimeout = Duration.ofMinutes(1),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private class FakeJobPort(private val attempt: Int = 1) : PlaceParsingJobPort {
        var completed = emptyList<PlaceCandidate>()
        var failedReason: String? = null
        var nextAttemptAt: Instant? = null
        var retryReason: String? = null

        override fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob =
            ClaimedPlaceParsingJob(postId, attempt, "본문", emptyList(), null)

        override fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob> = emptyList()

        override fun complete(postId: Long, places: List<PlaceCandidate>) {
            completed = places
        }

        override fun retry(postId: Long, nextAttemptAt: Instant, reason: String) {
            this.nextAttemptAt = nextAttemptAt
            retryReason = reason
        }

        override fun fail(postId: Long, reason: String) {
            failedReason = reason
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-28T00:00:00Z")
        val RETRY_BACKOFFS = listOf(
            Duration.ofSeconds(3),
            Duration.ofSeconds(3),
            Duration.ofSeconds(3),
        )

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
