package org.every.nook.api.infrastructure.persistence.post

import org.every.nook.api.application.content.SourceProfileHint
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PostContentParsingPersistenceAdapterTest {
    private val jobRepository = mock(PostContentParsingJobJpaRepository::class.java)
    private val postRepository = mock(PostJpaRepository::class.java)
    private val mediaRepository = mock(PostMediaJpaRepository::class.java)
    private val hashtagRepository = mock(PostHashtagJpaRepository::class.java)
    private val placeJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val followUpJobPort = mock(ParsingFollowUpJobPort::class.java)
    private val adapter = PostContentParsingPersistenceAdapter(
        jobRepository,
        postRepository,
        mediaRepository,
        hashtagRepository,
        placeJobRepository,
        eventPublisher,
        followUpJobPort,
        jacksonObjectMapper(),
        CLOCK,
    )

    init {
        `when`(jobRepository.findByPostIdForUpdate(101)).thenAnswer { jobRepository.findByPostId(101) }
    }

    @Test
    fun `ignores completion retry and failure from an expired attempt`() {
        val job = PostContentParsingJobEntity(
            postId = 101,
            status = PostContentParsingStatus.PROCESSING,
            attemptCount = 2,
        )
        `when`(jobRepository.findByPostIdForUpdate(101)).thenReturn(job)
        val stalePost = Post(
            source = PostSource("INSTAGRAM", "ABC123"),
            canonicalUrl = "https://www.instagram.com/p/ABC123/",
            title = null,
            body = "오래된 본문",
            hashtags = emptyList(),
            media = emptyList(),
        )

        assertFalse(adapter.complete(101, 1, stalePost, emptyList()))
        assertFalse(adapter.retry(101, 1, NOW.plusSeconds(60), "old retry"))
        assertFalse(adapter.fail(101, 1, "old failure"))

        assertEquals(PostContentParsingStatus.PROCESSING, job.status)
        assertEquals(2, job.attemptCount)
        assertEquals(null, job.failureReason)
        verifyNoInteractions(postRepository, mediaRepository, hashtagRepository, placeJobRepository, followUpJobPort)
    }

    @Test
    fun `claims an available job and returns only persisted input`() {
        val job = PostContentParsingJobEntity(
            postId = 101,
            status = PostContentParsingStatus.PENDING,
            nextAttemptAt = NOW,
        )
        val post = mock(PostEntity::class.java)
        `when`(post.canonicalUrl).thenReturn("https://www.instagram.com/p/ABC123/")
        `when`(jobRepository.findByPostIdForUpdate(101)).thenReturn(job)
        `when`(postRepository.findById(101)).thenReturn(Optional.of(post))

        val claimed = requireNotNull(adapter.claim(101, Duration.ofMinutes(2)))

        assertEquals(101, claimed.postId)
        assertEquals(1, claimed.attempt)
        assertEquals("https://www.instagram.com/p/ABC123/", claimed.canonicalUrl)
        assertEquals(PostContentParsingStatus.PROCESSING, job.status)
        assertEquals(1, job.attemptCount)
    }

    @Test
    fun `completes content and creates the dependent place job`() {
        val job = PostContentParsingJobEntity(101, PostContentParsingStatus.PROCESSING)
        val postEntity = mock(PostEntity::class.java)
        val placeJob = PlaceParsingJobEntity(101, PlaceParsingStatus.PENDING)
        val post = Post(
            source = PostSource("INSTAGRAM", "ABC123"),
            canonicalUrl = "https://www.instagram.com/p/ABC123/",
            title = "성수 맛집",
            body = "본문",
            hashtags = listOf("맛집"),
            media = listOf(
                PostMedia(
                    PostMedia.MediaType.VIDEO,
                    "https://source/video.mp4",
                    0,
                    "https://source/poster.jpg",
                ),
            ),
        )
        `when`(jobRepository.findByPostId(101)).thenReturn(job)
        `when`(jobRepository.findByPostIdForUpdate(101)).thenReturn(job)
        `when`(postRepository.findById(101)).thenReturn(Optional.of(postEntity))
        `when`(placeJobRepository.findByPostId(101)).thenReturn(null)
        `when`(placeJobRepository.save(any(PlaceParsingJobEntity::class.java))).thenReturn(placeJob)

        adapter.complete(
            postId = 101,
            attempt = 0,
            post = post,
            textPlaceClues = listOf(
                PlaceClue(
                    name = "성수 식당",
                    region = "성수",
                    queries = listOf("성수 식당"),
                    addressHint = "서울 성동구 성수이로 11 4층",
                ),
            ),
            imageTranscripts = listOf(ImageTranscript(1, listOf("성수 맛집"))),
            sourceProfileHints = listOf(SourceProfileHint("성수 식당", "seongsu.restaurant")),
        )

        assertEquals(PostContentParsingStatus.COMPLETED, job.status)
        verify(postEntity).updateContent(post)
        verify(mediaRepository).deleteAllByPostId(101)
        verify(hashtagRepository).deleteAllByPostId(101)
        val placeJobCaptor = ArgumentCaptor.forClass(PlaceParsingJobEntity::class.java)
        verify(placeJobRepository).save(placeJobCaptor.capture())
        assertEquals(PlaceParsingStatus.PENDING, placeJobCaptor.value.status)
        assertEquals(
            """[{"name":"성수 식당","region":"성수","queries":["성수 식당"],"evidence":[],""" +
                """"addressHint":"서울 성동구 성수이로 11 4층"}]""",
            placeJobCaptor.value.textPlaceClues,
        )
        assertEquals("""[{"imageIndex":1,"texts":["성수 맛집"]}]""", placeJobCaptor.value.imageTranscripts)
        assertSourceProfileHints(placeJobCaptor.value)
        assertFollowUpEvents()
    }

    private fun assertFollowUpEvents() {
        val placeEventCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher).publishEvent(placeEventCaptor.capture())
        val placeEvent = placeEventCaptor.value as PlaceParsingJobRequestedEvent
        val mediaEvent = mockingDetails(followUpJobPort).invocations
            .map { invocation -> invocation.arguments.single() }
            .filterIsInstance<PostMediaStorageRequestedEvent>()
            .single()
        assertEquals(101, placeEvent.postId)
        assertEquals("https://source/video.mp4", mediaEvent.sourceUrl)
        assertEquals("https://source/poster.jpg", mediaEvent.sourceThumbnailUrl)
    }

    private fun assertSourceProfileHints(placeJob: PlaceParsingJobEntity) {
        assertEquals(
            """[{"displayName":"성수 식당","username":"seongsu.restaurant"}]""",
            placeJob.sourceProfileHints,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
