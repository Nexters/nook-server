package org.every.nook.api.application.post

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProcessPostContentParsingJobUseCaseTest {
    @Test
    fun `extracts stores and completes post content outside the job claim`() {
        val calls = mutableListOf<String>()
        val port = RecordingPort(calls)
        val extractor = object : PostContentExtractor {
            override fun supports(url: String): Boolean = true

            override fun extract(url: String): ExtractedPostContent {
                calls += "extract"
                return ExtractedPostContent(
                    post = Post(
                        source = SOURCE,
                        canonicalUrl = url,
                        authorIdentifier = "nook",
                        body = "게시물 본문",
                        media = listOf(
                            PostMedia(PostMedia.MediaType.IMAGE, "https://source/image.jpg", 0),
                        ),
                    ),
                    hashtags = listOf("#맛집", "맛집", "서울"),
                    sourceLocationNames = listOf("", "성수"),
                )
            }
        }
        val useCase = ProcessPostContentParsingJobUseCase(
            jobPort = port,
            extractPostContent = ExtractPostContentUseCase(listOf(extractor)),
            contentInference = PostContentInference {
                calls += "inference"
                PostContentInference.Inference(
                    placeClues = listOf(PlaceClue("성수 식당", "성수", listOf("성수 식당"))),
                )
            },
            imageTextExtractor = ImageTextExtractor { request ->
                calls += "ocr"
                assertEquals("https://source/image.jpg", request.images.single().imageUrl)
                listOf(ImageTranscript(1, listOf("6월 2주차", "요즘 뜨고 있는 금주의 신상스폿")))
            },
            retryBackoffs = listOf(Duration.ofSeconds(3)),
            processingTimeout = Duration.ofMinutes(2),
            clock = CLOCK,
        )

        assertIs<ProcessPostContentParsingJobUseCase.Result.Completed>(useCase(101))
        assertEquals(
            listOf(
                "claim",
                "progress:CONTENT_FETCH",
                "extract",
                "progress:CONTENT_COVER_TITLE",
                "ocr",
                "progress:CONTENT_INFERENCE",
                "inference",
                "progress:CONTENT_SAVE",
                "complete",
            ),
            calls,
        )
        val completed = requireNotNull(port.completedPost)
        assertEquals(null, completed.title)
        assertEquals("성수", completed.sourceLocationTag)
        assertEquals(listOf("맛집", "서울"), completed.hashtags)
        assertEquals("https://source/image.jpg", completed.media.single().url)
        assertEquals(listOf("성수 식당"), port.completedTextPlaceClues.map(PlaceClue::name))
        assertEquals(listOf("6월 2주차", "요즘 뜨고 있는 금주의 신상스폿"), port.completedImageTranscripts.single().texts)
    }

    @Test
    fun `schedules retry without completing partial content`() {
        val port = RecordingPort()
        val useCase = ProcessPostContentParsingJobUseCase(
            jobPort = port,
            extractPostContent = ExtractPostContentUseCase(
                listOf(
                    object : PostContentExtractor {
                        override fun supports(url: String): Boolean = true

                        override fun extract(url: String): ExtractedPostContent = error("provider timeout")
                    },
                ),
            ),
            contentInference = PostContentInference { error("content must not be inferred") },
            retryBackoffs = listOf(Duration.ofSeconds(3)),
            processingTimeout = Duration.ofMinutes(2),
            clock = CLOCK,
        )

        val result = assertIs<ProcessPostContentParsingJobUseCase.Result.Retry>(useCase(101))

        assertEquals(NOW.plusSeconds(3), result.nextAttemptAt)
        assertEquals(NOW.plusSeconds(3), port.retryAt)
        assertEquals("provider timeout", port.failureReason)
        assertEquals(null, port.completedPost)
    }

    @Test
    fun `does not retry content that the provider reports as missing`() {
        val port = RecordingPort()
        val useCase = ProcessPostContentParsingJobUseCase(
            jobPort = port,
            extractPostContent = ExtractPostContentUseCase(
                listOf(
                    object : PostContentExtractor {
                        override fun supports(url: String): Boolean = true

                        override fun extract(url: String): ExtractedPostContent = throw PostContentNotFoundException()
                    },
                ),
            ),
            contentInference = PostContentInference { error("content must not be inferred") },
            retryBackoffs = listOf(Duration.ofSeconds(3)),
            processingTimeout = Duration.ofMinutes(2),
            clock = CLOCK,
        )

        assertEquals(ProcessPostContentParsingJobUseCase.Result.Failed, useCase(101))
        assertEquals(null, port.retryAt)
        assertEquals("게시물 콘텐츠를 찾을 수 없습니다.", port.failureReason)
    }

    @Test
    fun `records each content processing stage`() {
        val measurements = mutableListOf<ProcessingMetrics.Measurement>()
        val port = RecordingPort()
        val extractor = object : PostContentExtractor {
            override fun supports(url: String): Boolean = true

            override fun extract(url: String): ExtractedPostContent = ExtractedPostContent(
                post = Post(source = SOURCE, canonicalUrl = url),
                hashtags = emptyList(),
                sourceLocationNames = emptyList(),
            )
        }
        val useCase = ProcessPostContentParsingJobUseCase(
            jobPort = port,
            extractPostContent = ExtractPostContentUseCase(listOf(extractor)),
            contentInference = PostContentInference {
                PostContentInference.Inference(emptyList())
            },
            retryBackoffs = listOf(Duration.ofSeconds(3)),
            processingTimeout = Duration.ofMinutes(2),
            metrics = ProcessingMetrics(measurements::add),
            clock = CLOCK,
        )

        assertIs<ProcessPostContentParsingJobUseCase.Result.Completed>(useCase(101))
        assertEquals(listOf("extract", "inference", "complete"), measurements.map { it.stage })
        assertEquals(listOf("post-content", "post-content", "post-content"), measurements.map { it.flow })
    }

    private class RecordingPort(private val calls: MutableList<String> = mutableListOf()) :
        PostContentParsingJobPort {
        var completedPost: Post? = null
        var completedTextPlaceClues: List<PlaceClue> = emptyList()
        var completedImageTranscripts: List<ImageTranscript> = emptyList()
        var retryAt: Instant? = null
        var failureReason: String? = null

        override fun claim(postId: Long, processingTimeout: Duration): ClaimedPostContentParsingJob {
            calls += "claim"
            return ClaimedPostContentParsingJob(
                postId = postId,
                attempt = 1,
                canonicalUrl = "https://www.instagram.com/p/ABC123/",
            )
        }

        override fun findOutstanding(processingTimeout: Duration): List<OutstandingPostContentParsingJob> = emptyList()

        override fun updateProgress(postId: Long, stage: ParsingProgressStage) {
            calls += "progress:${stage.name}"
        }

        override fun complete(
            postId: Long,
            post: Post,
            textPlaceClues: List<PlaceClue>,
            imageTranscripts: List<ImageTranscript>,
        ) {
            calls += "complete"
            completedPost = post
            completedTextPlaceClues = textPlaceClues
            completedImageTranscripts = imageTranscripts
        }

        override fun retry(postId: Long, nextAttemptAt: Instant, reason: String) {
            retryAt = nextAttemptAt
            failureReason = reason
        }

        override fun fail(postId: Long, reason: String) {
            failureReason = reason
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val SOURCE = PostSource("INSTAGRAM", "ABC123")
    }
}
