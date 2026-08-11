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
        val port = FakeJobPort(body = "원동미나리삼겹살과 서울역")
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
        val useCase = useCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase(provider),
            PlaceCandidateSelector { error("fallback selector must not be called") },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf("1", "2"), port.completed.map { it.externalPlaceId })
        assertNull(port.failedReason)
    }

    @Test
    fun `skips image fallback when text resolves at least one place`() {
        val port = FakeJobPort(body = "텍스트 장소", imageUrls = listOf("https://cdn.test/1.jpg"))
        val requests = mutableListOf<PlaceClueExtractor.Request>()
        val extractor = PlaceClueExtractor { request ->
            requests += request
            listOf(PlaceClue("텍스트 장소", "서울", listOf("텍스트 장소")))
        }
        val useCase = useCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase {
                listOf(candidate("1", "텍스트 장소", "서울 중구"))
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(1, requests.size)
        assertEquals(emptyList(), requests.single().imageUrls)
    }

    @Test
    fun `reuses stored text clues without another text inference call`() {
        val port = FakeJobPort(
            body = "저장된 장소",
            textClues = listOf(PlaceClue("저장된 장소", "서울", listOf("저장된 장소"))),
        )
        val useCase = useCase(
            port,
            PlaceClueExtractor { error("text inference must not be called") },
            SearchPlaceCandidatesUseCase {
                listOf(candidate("1", "저장된 장소", "서울 중구"))
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `rejects an ungrounded text clue before searching and uses image evidence`() {
        val port = FakeJobPort(
            body = "느좋카페 10선",
            imageUrls = listOf("https://cdn.test/1.jpg"),
        )
        val requests = mutableListOf<PlaceClueExtractor.Request>()
        val searchedQueries = mutableListOf<String>()
        val extractor = PlaceClueExtractor { request ->
            requests += request
            if (request.imageUrls.isEmpty()) {
                listOf(PlaceClue("무심", null, listOf("무심")))
            } else {
                listOf(
                    PlaceClue(
                        "원형들",
                        "서울 중구",
                        listOf("원형들"),
                        listOf(PlaceClueEvidence(1, "원형들 / 서울 중구 창경궁로1길 38")),
                    ),
                )
            }
        }
        val provider = PlaceSearchProvider { request ->
            searchedQueries += request.query
            listOf(candidate("1", "원형들", "서울 중구 창경궁로1길 38"))
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(2, requests.size)
        assertEquals(listOf("원형들"), searchedQueries)
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `merges image places when text results do not meet the expected count`() {
        val port = FakeJobPort(
            body = "카페 2곳: 텍스트 장소",
            imageUrls = listOf("https://cdn.test/1.jpg", "https://cdn.test/2.jpg"),
        )
        val requests = mutableListOf<PlaceClueExtractor.Request>()
        val extractor = PlaceClueExtractor { request ->
            requests += request
            if (request.imageUrls.isEmpty()) {
                listOf(PlaceClue("텍스트 장소", "서울", listOf("텍스트 장소")))
            } else {
                listOf(
                    PlaceClue(
                        "텍스트 장소",
                        "서울",
                        listOf("텍스트 장소"),
                        listOf(PlaceClueEvidence(1, "텍스트 장소 / 서울 중구")),
                    ),
                    PlaceClue(
                        "이미지 장소",
                        "서울",
                        listOf("이미지 장소"),
                        listOf(PlaceClueEvidence(2, "이미지 장소 / 서울 종로구")),
                    ),
                )
            }
        }
        val provider = PlaceSearchProvider { request ->
            when (request.query) {
                "텍스트 장소" -> listOf(candidate("1", "텍스트 장소", "서울 중구"))
                else -> listOf(candidate("2", "이미지 장소", "서울 종로구"))
            }
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(2, requests.size)
        assertEquals(listOf("1", "2"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `uses at most twenty images in one fallback extraction`() {
        val imageUrls = (0 until 25).map { "https://cdn.test/$it.jpg" }
        val port = FakeJobPort(imageUrls = imageUrls)
        val requests = mutableListOf<PlaceClueExtractor.Request>()
        val extractor = PlaceClueExtractor { request ->
            requests += request
            if (request.imageUrls.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    PlaceClue(
                        "이미지 장소",
                        "서울",
                        listOf("이미지 장소"),
                        listOf(PlaceClueEvidence(1, "이미지 장소 / 서울 중구")),
                    ),
                )
            }
        }
        val useCase = useCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase {
                listOf(candidate("1", "이미지 장소", "서울 중구"))
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(2, requests.size)
        assertEquals(imageUrls.take(20), requests.last().imageUrls)
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `uses image fallback when every text clue fails to resolve`() {
        val port = FakeJobPort(imageUrls = listOf("https://cdn.test/1.jpg"))
        val requests = mutableListOf<PlaceClueExtractor.Request>()
        val extractor = PlaceClueExtractor { request ->
            requests += request
            if (request.imageUrls.isEmpty()) {
                listOf(PlaceClue("잘못 읽은 장소", null, listOf("잘못 읽은 장소")))
            } else {
                listOf(
                    PlaceClue(
                        "이미지 장소",
                        null,
                        listOf("이미지 장소"),
                        listOf(PlaceClueEvidence(1, "이미지 장소")),
                    ),
                )
            }
        }
        val useCase = useCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase { request ->
                if (request.query == "이미지 장소") {
                    listOf(candidate("1", "이미지 장소", "서울 중구"))
                } else {
                    emptyList()
                }
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(2, requests.size)
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `stops searching after the first query resolves one strict match`() {
        val port = FakeJobPort(body = "Lodge190")
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
        assertEquals(listOf("Lodge190"), searchedQueries)
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
    }

    @Test
    fun `completes with resolved places when some place clues do not match`() {
        val port = FakeJobPort(body = "첫 번째 장소, 매칭 실패 장소, 두 번째 장소")
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
    fun `resolves a single exact name candidate despite an incorrect region`() {
        val port = FakeJobPort(body = "이츠야")
        val extractor = PlaceClueExtractor {
            listOf(
                PlaceClue(
                    name = "이츠야",
                    region = "서울특별시 서초구 상수역 인근",
                    queries = listOf("이츠야", "상수동 이츠야"),
                ),
            )
        }
        val provider = PlaceSearchProvider {
            listOf(candidate("1", "이츠야", "서울 마포구 양화로6길 99-9"))
        }
        val selector = PlaceCandidateSelector { request ->
            assertEquals(1, request.candidates.size)
            assertEquals(listOf("이츠야", "상수동 이츠야"), request.candidates.single().matchedQueries)
            request.candidates.single().place
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider), selector)

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf("1"), port.completed.map { it.externalPlaceId })
        assertNull(port.failedReason)
    }

    @Test
    fun `does not choose arbitrarily when multiple exact name candidates do not match region`() {
        val port = FakeJobPort(attempt = 4, body = "동일상호")
        val extractor = PlaceClueExtractor {
            listOf(PlaceClue("동일상호", "서초구", listOf("동일상호")))
        }
        val provider = PlaceSearchProvider {
            listOf(
                candidate("1", "동일상호", "서울 마포구"),
                candidate("2", "동일상호", "서울 종로구"),
            )
        }
        val useCase = useCase(port, extractor, SearchPlaceCandidatesUseCase(provider))

        assertIs<ProcessPlaceParsingJobUseCase.Result.Failed>(useCase(1))
        assertEquals(emptyList(), port.completed)
        assertEquals("No place candidate selected: 동일상호, strictMatchCount=0", port.failedReason)
    }

    @Test
    fun `fails without retry when every place clue fails to match and no image is available`() {
        val port = FakeJobPort(attempt = 2, body = "매칭 실패 장소")
        val extractor = PlaceClueExtractor {
            listOf(PlaceClue("매칭 실패 장소", "서울", listOf("매칭 실패 장소")))
        }
        val useCase = useCase(
            port,
            extractor,
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Failed>(useCase(1))
        assertNull(port.nextAttemptAt)
        assertEquals("No place candidate found: 매칭 실패 장소", port.failedReason)
        assertEquals(emptyList(), port.completed)
    }

    @Test
    fun `retries the whole job when place search provider fails after a resolved clue`() {
        val port = FakeJobPort(attempt = 2, body = "정상 장소와 검색 오류 장소")
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
    fun `fails without retry when neither text nor images provide a place clue`() {
        val port = FakeJobPort(imageUrls = listOf("https://cdn.test/1.jpg"))
        val useCase = useCase(
            port,
            PlaceClueExtractor { emptyList() },
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Failed>(useCase(1))
        assertEquals("No place could be resolved after image analysis", port.failedReason)
        assertNull(port.nextAttemptAt)
    }

    @Test
    fun `fails without retry when no image is available for fallback`() {
        val port = FakeJobPort()
        val useCase = useCase(
            port,
            PlaceClueExtractor { emptyList() },
            SearchPlaceCandidatesUseCase { emptyList() },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Failed>(useCase(1))
        assertEquals("No place could be resolved from text", port.failedReason)
        assertNull(port.nextAttemptAt)
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
        selector: PlaceCandidateSelector = PlaceCandidateSelector { null },
    ): ProcessPlaceParsingJobUseCase = ProcessPlaceParsingJobUseCase(
        jobPort = port,
        clueExtractor = extractor,
        searchPlaceCandidates = search,
        candidateSelector = selector,
        retryBackoffs = RETRY_BACKOFFS,
        processingTimeout = Duration.ofMinutes(1),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private class FakeJobPort(
        private val attempt: Int = 1,
        private val body: String = "본문",
        private val imageUrls: List<String> = emptyList(),
        private val textClues: List<PlaceClue>? = null,
    ) : PlaceParsingJobPort {
        var completed = emptyList<PlaceCandidate>()
        var failedReason: String? = null
        var nextAttemptAt: Instant? = null
        var retryReason: String? = null

        override fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob = ClaimedPlaceParsingJob(
            postId = postId,
            attempt = attempt,
            body = body,
            hashtags = emptyList(),
            sourceLocationTag = null,
            imageUrls = imageUrls,
            textClues = textClues,
        )

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
