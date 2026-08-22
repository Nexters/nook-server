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
import kotlin.test.assertTrue

class PlaceParsingResilienceTest {
    @Test
    fun `retries an empty transcript with the refreshed stored image`() {
        val requestedUrls = mutableListOf<String>()
        val transcripts = extractImageTranscripts(
            extractor = ImageTextExtractor { request ->
                val image = request.images.single()
                requestedUrls += image.imageUrl
                listOf(
                    ImageTranscript(
                        image.imageIndex,
                        if (image.imageUrl.contains("stored")) listOf("가람상점", "서울 성동구 푸른길 1") else emptyList(),
                    ),
                )
            },
            images = listOf(ImageTextExtractor.ImageInput(5, "https://instagram.test/5.jpg")),
            concurrency = 1,
            fallbackImage = { ImageTextExtractor.ImageInput(5, "https://stored.test/5.jpg") },
        )

        assertEquals(listOf("https://instagram.test/5.jpg", "https://stored.test/5.jpg"), requestedUrls)
        assertEquals(listOf("가람상점", "서울 성동구 푸른길 1"), transcripts.single().texts)
    }

    @Test
    fun `normalizes partial duplicate and unexpected transcript indexes without failing the job`() {
        val port = FakeJobPort(imageUrls = listOf("https://cdn.test/1.jpg", "https://cdn.test/2.jpg"))
        val useCase = useCase(
            port = port,
            clueExtractor = PlaceClueExtractor { request ->
                if (request.imageTranscripts.isEmpty()) emptyList() else listOf(imageClue("이미지 장소", 1))
            },
            imageTextExtractor = ImageTextExtractor {
                listOf(
                    ImageTranscript(1, listOf("이미지 장소")),
                    ImageTranscript(1, listOf("서울 중구")),
                    ImageTranscript(99, listOf("요청하지 않은 전사")),
                )
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf(1, 2), port.storedImageTranscripts?.map(ImageTranscript::imageIndex))
        assertEquals(listOf("이미지 장소", "서울 중구"), port.storedImageTranscripts?.first()?.texts)
        assertEquals(emptyList(), port.storedImageTranscripts?.last()?.texts)
        assertEquals(listOf("1"), port.completed.map(PlaceCandidate::externalPlaceId))
    }

    @Test
    fun `recovers unused image clues and retranscribes only insufficient unused images`() {
        val port = FakeJobPort(body = "카페 4곳", imageUrls = (1..4).map { "https://cdn.test/$it.jpg" })
        val clueRequests = mutableListOf<PlaceClueExtractor.Request>()
        val transcriptRequests = java.util.Collections.synchronizedList(mutableListOf<ImageTextExtractor.Request>())
        val callsByImage = java.util.concurrent.ConcurrentHashMap<Int, Int>()
        val useCase = useCase(
            port = port,
            clueExtractor = PlaceClueExtractor { request ->
                clueRequests += request
                when (clueRequests.size) {
                    1 -> emptyList()

                    2 -> listOf(imageClue("기존 장소", 1))

                    else -> listOf(
                        imageClue("두 번째 장소", 2),
                        imageClue("세 번째 장소", 3),
                        imageClue("네 번째 장소", 4),
                    )
                }
            },
            imageTextExtractor = ImageTextExtractor { request ->
                transcriptRequests += request
                val image = request.images.single()
                val call = callsByImage.merge(image.imageIndex, 1, Int::plus)
                val texts = when (image.imageIndex) {
                    1 -> listOf("기존 장소", "서울 중구 세종대로 1")

                    2 -> listOf("두 번째 장소", "서울 종로구 종로 2")

                    3 -> listOf("세 번째 장소", "서울 용산구 한강대로 3")

                    else -> if (call == 1) {
                        listOf("공통 워터마크")
                    } else {
                        listOf("네 번째 장소", "서울 마포구 양화로 4")
                    }
                }
                listOf(ImageTranscript(image.imageIndex, texts))
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 2), callsByImage)
        assertEquals(setOf(1), transcriptRequests.map { it.images.size }.toSet())
        assertEquals(listOf(2, 3, 4), clueRequests.last().imageTranscripts.map(ImageTranscript::imageIndex))
        assertEquals(listOf("1", "2", "3", "4"), port.completed.map(PlaceCandidate::externalPlaceId))
        assertEquals(4, port.diagnostics?.expectedPlaceCount)
        assertEquals(
            listOf("공통 워터마크", "네 번째 장소", "서울 마포구 양화로 4"),
            port.storedImageTranscripts?.last()?.texts,
        )
    }

    @Test
    fun `counts image place cards from separate names and explicit addresses`() {
        val transcripts = listOf(
            ImageTranscript(1, listOf("표지", "카페 모음")),
            ImageTranscript(2, listOf("첫 장소", "서울 중구 세종대로 1")),
            ImageTranscript(3, listOf("둘째 장소", "서울 마포구 양화로 2")),
        )

        assertEquals(2, transcripts.detectedPlaceCardCount())
    }

    @Test
    fun `counts flattened Corepin transcripts and deduplicates a repeated place card`() {
        val transcripts = listOf(
            ImageTranscript(1, listOf("서울 카페 모음")),
            ImageTranscript(2, listOf("몬 서울특별시 마포구 가람로37길 1 지1층 조용한 카페")),
            ImageTranscript(3, listOf("모멘트 서울 광진구 나람로32길 50 1층 코너")),
            ImageTranscript(4, listOf("들꽃상점 서울 용산구 푸른로 208 4층")),
            ImageTranscript(5, listOf("공유 문구 들꽃상점 서울 용산구 푸른로 208 4층")),
        )

        assertEquals(3, transcripts.detectedPlaceCardCount())
    }

    @Test
    fun `restores a one-character place name immediately before an address`() {
        val transcript = ImageTranscript(
            7,
            listOf("몬 서울특별시 마포구 가람로37길 1 지1층 조용한 카페"),
        )
        val clue = PlaceClue(
            name = "근처 카페",
            region = "서울 마포구",
            queries = listOf("근처 카페"),
            addressHint = "서울특별시 마포구 가람로37길 1 지1층",
            evidence = listOf(PlaceClueEvidence(7, transcript.texts.single())),
        )

        assertEquals("몬", clue.restoreGroundingFromCard(listOf(transcript)).name)
    }

    @Test
    fun `restores a multi-character place name when inference replaces it with the account name`() {
        val transcript = ImageTranscript(
            4,
            listOf("가람커피로스터스 서울 성동구 푸른로11길 10 1층 채광이 좋아요 sample.account"),
        )
        val clue = PlaceClue(
            name = "SAMPLE.ACCOUNT",
            region = "서울",
            queries = listOf("SAMPLE.ACCOUNT 서울"),
            addressHint = "가람커피로스터스 서울 성동구 푸른로11길 10 1층",
            evidence = listOf(PlaceClueEvidence(4, transcript.texts.single())),
        )

        assertEquals("가람커피로스터스", clue.restoreGroundingFromCard(listOf(transcript)).name)
    }

    @Test
    fun `restores the explicit OCR address when inference replaces it with a vague landmark`() {
        val transcript = ImageTranscript(
            7,
            listOf("홈 서울특별시 마포구 와우산로37길 1 지1층 홍대가면 경의선 책거리 쪽이 여유롭습니다"),
        )
        val clue = PlaceClue(
            name = "홈",
            region = "서울 마포구",
            queries = listOf("홈", "홍대 카페 경의선 책거리"),
            addressHint = "마포구 경의선 책거리 쪽",
            evidence = listOf(PlaceClueEvidence(7, "홍대가면 경의선 책거리 쪽이 여유롭습니다")),
        )

        val restored = clue.restoreGroundingFromCard(listOf(transcript))

        assertEquals("서울특별시 마포구 와우산로37길 1 지1층", restored.addressHint)
        assertEquals(transcript.texts.single(), restored.evidence.single().evidenceText)
        assertTrue("서울특별시 마포구 와우산로37길 1 지1층" in restored.queries)
    }

    @Test
    fun `replaces generated evidence with the uniquely matching OCR transcript`() {
        val transcripts = listOf(
            ImageTranscript(3, listOf("Knewnew 2umoae | 숙대입구역 채광 좋은 창가")),
            ImageTranscript(4, listOf("3 퍼머넌트해비탯 | 뚝섬역 따뜻한 무드의 카페")),
        )
        val clue = PlaceClue(
            name = "umoae",
            region = "서울 용산구",
            queries = listOf("umoae", "Umoae near Sookdae"),
            addressHint = "서울 용산구 한강대로84길 21-17 1층",
            evidence = listOf(PlaceClueEvidence(3, transcripts[1].texts.single())),
        )

        val restored = clue.restoreGroundingFromCard(transcripts)

        assertEquals(3, restored.evidence.single().imageIndex)
        assertEquals(transcripts[0].texts.single(), restored.evidence.single().evidenceText)
    }

    @Test
    fun `keeps primary places when recall recovery fails`() {
        val port = FakeJobPort(
            body = "카페 2곳",
            imageUrls = listOf("https://cdn.test/1.jpg", "https://cdn.test/2.jpg"),
        )
        var clueCallCount = 0
        val useCase = useCase(
            port = port,
            clueExtractor = PlaceClueExtractor {
                clueCallCount += 1
                when (clueCallCount) {
                    1 -> emptyList()
                    2 -> listOf(imageClue("확보한 장소", 1))
                    else -> error("recovery inference failed")
                }
            },
            imageTextExtractor = ImageTextExtractor { request ->
                request.images.map { image ->
                    ImageTranscript(image.imageIndex, listOf("장소 ${image.imageIndex}", "서울 주소"))
                }
            },
        )

        assertIs<ProcessPlaceParsingJobUseCase.Result.Completed>(useCase(1))
        assertEquals(listOf("1"), port.completed.map(PlaceCandidate::externalPlaceId))
        assertNull(port.failedReason)
        assertNull(port.nextAttemptAt)
    }

    private fun useCase(
        port: FakeJobPort,
        clueExtractor: PlaceClueExtractor,
        imageTextExtractor: ImageTextExtractor,
    ): ProcessPlaceParsingJobUseCase = ProcessPlaceParsingJobUseCase(
        jobPort = port,
        imageTextExtractor = imageTextExtractor,
        clueExtractor = clueExtractor,
        searchPlaceCandidates = SearchPlaceCandidatesUseCase { request ->
            val id = when (request.query) {
                "두 번째 장소" -> "2"
                "세 번째 장소" -> "3"
                "네 번째 장소" -> "4"
                else -> "1"
            }
            listOf(candidate(id, request.query, "서울"))
        },
        candidateSelector = PlaceCandidateSelector { null },
        retryBackoffs = listOf(Duration.ofSeconds(3)),
        processingTimeout = Duration.ofMinutes(1),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private class FakeJobPort(private val body: String = "본문", private val imageUrls: List<String>) :
        PlaceParsingJobPort {
        var completed = emptyList<PlaceCandidate>()
        var failedReason: String? = null
        var nextAttemptAt: Instant? = null
        var storedImageTranscripts: List<ImageTranscript>? = null
        var diagnostics: PlaceParsingDiagnostics? = null

        override fun claim(postId: Long, processingTimeout: Duration) = ClaimedPlaceParsingJob(
            postId = postId,
            attempt = 1,
            body = body,
            hashtags = emptyList(),
            sourceLocationTag = null,
            imageUrls = imageUrls,
        )

        override fun findOutstanding(processingTimeout: Duration) = emptyList<OutstandingPlaceParsingJob>()

        override fun storeImageTranscripts(postId: Long, transcripts: List<ImageTranscript>) {
            storedImageTranscripts = transcripts
        }

        override fun complete(
            postId: Long,
            title: String?,
            places: List<PlaceCandidate>,
            diagnostics: PlaceParsingDiagnostics,
        ) {
            completed = places
            this.diagnostics = diagnostics
        }

        override fun retry(postId: Long, nextAttemptAt: Instant, reason: String) {
            this.nextAttemptAt = nextAttemptAt
        }

        override fun fail(postId: Long, title: String, reason: String) {
            failedReason = reason
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-12T00:00:00Z")

        fun imageClue(name: String, imageIndex: Int) = PlaceClue(
            name = name,
            region = "서울",
            queries = listOf(name),
            evidence = listOf(PlaceClueEvidence(imageIndex, "$name / 서울")),
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
