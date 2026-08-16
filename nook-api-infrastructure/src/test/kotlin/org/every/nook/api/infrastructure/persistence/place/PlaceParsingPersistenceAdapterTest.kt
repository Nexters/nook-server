package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.InferredPlaceTag
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceTagEvidenceSource
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceParsingPersistenceAdapterTest {
    private val jobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val postRepository = mock(PostJpaRepository::class.java)
    private val hashtagRepository = mock(PostHashtagJpaRepository::class.java)
    private val mediaRepository = mock(PostMediaJpaRepository::class.java)
    private val placeRepository = mock(PlaceJpaRepository::class.java)
    private val postPlaceRepository = mock(PostPlaceJpaRepository::class.java)
    private val userSavedPostLockRepository = mock(UserSavedPostLockJpaRepository::class.java)
    private val userSavedPostPlaceRepository = mock(UserSavedPostPlaceJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val postPlaceTagRepository = mock(PostPlaceTagJpaRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val adapter = PlaceParsingPersistenceAdapter(
        jobRepository = jobRepository,
        postRepository = postRepository,
        hashtagRepository = hashtagRepository,
        mediaRepository = mediaRepository,
        placeRepository = placeRepository,
        postPlaceRepository = postPlaceRepository,
        userSavedPostLockRepository = userSavedPostLockRepository,
        userSavedPostPlaceRepository = userSavedPostPlaceRepository,
        userPlaceBookmarkRepository = bookmarkRepository,
        postPlaceTagRepository = postPlaceTagRepository,
        eventPublisher = eventPublisher,
        objectMapper = jacksonObjectMapper(),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `claims an available pending job and increments its attempt`() {
        val job = PlaceParsingJobEntity(
            postId = 11,
            status = PlaceParsingStatus.PENDING,
            failureReason = "previous failure",
            attemptCount = 1,
            nextAttemptAt = NOW.minusSeconds(1),
        )
        val post = mock(org.every.nook.api.infrastructure.persistence.post.PostEntity::class.java)
        `when`(jobRepository.findByPostIdForUpdate(11)).thenReturn(job)
        `when`(postRepository.findById(11)).thenReturn(Optional.of(post))
        `when`(hashtagRepository.findAllByPostIdOrderBySequenceAsc(11)).thenReturn(emptyList())
        `when`(
            mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(
            listOf(
                PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://cdn.test/1.jpg", 0),
                PostMediaEntity(11, PostMedia.MediaType.IMAGE, "https://cdn.test/2.jpg", 1),
            ),
        )

        val claimed = requireNotNull(adapter.claim(11, Duration.ofMinutes(1)))

        assertEquals(2, claimed.attempt)
        assertEquals(listOf("https://cdn.test/1.jpg", "https://cdn.test/2.jpg"), claimed.imageUrls)
        assertEquals(PlaceParsingStatus.PROCESSING, job.status)
        assertEquals("previous failure", job.failureReason)
        assertEquals(NOW, job.nextAttemptAt)
    }

    @Test
    fun `returns stored text clues when claiming a new inference job`() {
        val job = PlaceParsingJobEntity(
            postId = 11,
            status = PlaceParsingStatus.PENDING,
            textPlaceClues = """[{"name":"성수 식당","region":"성수","queries":["성수 식당"],"evidence":[]}]""",
            nextAttemptAt = NOW.minusSeconds(1),
        )
        val post = mock(PostEntity::class.java)
        `when`(jobRepository.findByPostIdForUpdate(11)).thenReturn(job)
        `when`(postRepository.findById(11)).thenReturn(Optional.of(post))
        `when`(hashtagRepository.findAllByPostIdOrderBySequenceAsc(11)).thenReturn(emptyList())
        `when`(
            mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(emptyList())

        val claimed = requireNotNull(adapter.claim(11, Duration.ofMinutes(1)))

        assertEquals(listOf("성수 식당"), claimed.textClues?.map(PlaceClue::name))
        assertEquals(null, claimed.textClues?.single()?.addressHint)
    }

    @Test
    fun `returns stored image transcripts when claiming a retry`() {
        val job = PlaceParsingJobEntity(
            postId = 11,
            status = PlaceParsingStatus.PENDING,
            imageTranscripts = """[{"imageIndex":1,"texts":["원형들","서울 중구"]}]""",
            nextAttemptAt = NOW.minusSeconds(1),
        )
        val post = mock(PostEntity::class.java)
        `when`(jobRepository.findByPostIdForUpdate(11)).thenReturn(job)
        `when`(postRepository.findById(11)).thenReturn(Optional.of(post))
        `when`(hashtagRepository.findAllByPostIdOrderBySequenceAsc(11)).thenReturn(emptyList())
        `when`(
            mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(emptyList())

        val claimed = requireNotNull(adapter.claim(11, Duration.ofMinutes(1)))

        assertEquals(listOf("원형들", "서울 중구"), claimed.imageTranscripts?.single()?.texts)
    }

    @Test
    fun `stores image transcripts on a processing job`() {
        val job = PlaceParsingJobEntity(postId = 11, status = PlaceParsingStatus.PROCESSING)
        `when`(jobRepository.findByPostId(11)).thenReturn(job)

        adapter.storeImageTranscripts(11, listOf(ImageTranscript(1, listOf("원형들", "서울 중구"))))

        assertEquals("""[{"imageIndex":1,"texts":["원형들","서울 중구"]}]""", job.imageTranscripts)
    }

    @Test
    fun `retains the latest failure reason while waiting for retry`() {
        val job = PlaceParsingJobEntity(postId = 11, status = PlaceParsingStatus.PROCESSING)
        `when`(jobRepository.findByPostId(11)).thenReturn(job)

        adapter.retry(
            postId = 11,
            nextAttemptAt = NOW.plusSeconds(3),
            reason = "No place candidate matched: Lodge190",
        )

        assertEquals(PlaceParsingStatus.PENDING, job.status)
        assertEquals("No place candidate matched: Lodge190", job.failureReason)
        assertEquals(NOW.plusSeconds(3), job.nextAttemptAt)
    }

    @Test
    fun `does not claim a pending job before its backoff expires`() {
        val job = PlaceParsingJobEntity(
            postId = 11,
            status = PlaceParsingStatus.PENDING,
            nextAttemptAt = NOW.plusSeconds(1),
        )
        `when`(jobRepository.findByPostIdForUpdate(11)).thenReturn(job)

        assertEquals(null, adapter.claim(11, Duration.ofMinutes(1)))
        assertEquals(0, job.attemptCount)
    }

    @Test
    fun `completed places are bookmarked on by default for every user who saved the post`() {
        val job = PlaceParsingJobEntity(postId = 11, status = PlaceParsingStatus.PROCESSING)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(jobRepository.findByPostId(11)).thenReturn(job)
        `when`(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "123")).thenReturn(place)
        val savedPosts = listOf(
            savedPostWithMemo(userId = 7, memo = "내 메모"),
            savedPostWithMemo(userId = 8, memo = null),
        )
        `when`(userSavedPostLockRepository.findAllByPostIdForUpdate(11)).thenReturn(savedPosts)
        `when`(userSavedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(21))
            .thenReturn(listOf(UserSavedPostPlaceEntity(21, 17, 0)))
        `when`(userSavedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(22))
            .thenReturn(listOf(UserSavedPostPlaceEntity(22, 17, 0)))

        adapter.complete(
            postId = 11,
            places = listOf(
                PlaceCandidate(
                    provider = "KAKAO",
                    externalPlaceId = "123",
                    name = "Nook Cafe",
                    address = "Seoul",
                    latitude = BigDecimal("37.1"),
                    longitude = BigDecimal("127.1"),
                    category = null,
                    phoneNumber = null,
                    providerUrl = null,
                ),
            ),
        )

        verify(userSavedPostPlaceRepository).insertAllFromPost(21, 11)
        verify(userSavedPostPlaceRepository).insertAllFromPost(22, 11)
        verify(bookmarkRepository).insertIgnoreWithMemo(7, 17, "내 메모")
        verify(bookmarkRepository).insertIgnoreWithMemo(8, 17, null)
        assertEquals(PlaceParsingStatus.COMPLETED, job.status)
    }

    @Test
    fun `stores an extracted city when a parsed place is new`() {
        val job = PlaceParsingJobEntity(postId = 11, status = PlaceParsingStatus.PROCESSING)
        val savedPlace = mock(PlaceEntity::class.java)
        `when`(savedPlace.id).thenReturn(17)
        `when`(jobRepository.findByPostId(11)).thenReturn(job)
        `when`(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "123")).thenReturn(null)
        `when`(placeRepository.save(any(PlaceEntity::class.java))).thenReturn(savedPlace)
        `when`(userSavedPostLockRepository.findAllByPostIdForUpdate(11)).thenReturn(emptyList())

        adapter.complete(
            postId = 11,
            places = listOf(
                PlaceCandidate(
                    provider = "KAKAO",
                    externalPlaceId = "123",
                    name = "누크 카페",
                    address = "경기도 성남시 분당구 판교역로 1",
                    latitude = BigDecimal("37.1"),
                    longitude = BigDecimal("127.1"),
                    category = "카페",
                    phoneNumber = null,
                    providerUrl = null,
                ),
            ),
        )

        val captor = ArgumentCaptor.forClass(PlaceEntity::class.java)
        verify(placeRepository).save(captor.capture())
        assertEquals("성남", captor.value.city)
    }

    @Test
    fun `updates a place thumbnail independently after parsing completes`() {
        val place = mock(PlaceEntity::class.java)
        `when`(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "123")).thenReturn(place)

        val supplement = PlaceSupplement(null, listOf("https://cdn.example.com/google-place.jpg"))
        adapter.update("KAKAO", "123", PlaceThumbnailParsingStatus.COMPLETED, supplement)

        verify(place).updateThumbnailParsing(PlaceThumbnailParsingStatus.COMPLETED, supplement)
    }

    @Test
    fun `replaces post place tags and updates representative tags`() {
        val place = mock(PlaceEntity::class.java)
        val inferred = InferredPlaceTag(PlaceTag.QUIET, 0.9, PlaceTagEvidenceSource.BODY, "조용해요")
        `when`(postPlaceRepository.findByPostIdAndPlaceId(11, 17)).thenReturn(PostPlaceEntity(11, 17, 0))
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(postPlaceTagRepository.findRepresentativeTags(17))
            .thenReturn(listOf(PlaceTag.QUIET, PlaceTag.SOLO_DINING))

        adapter.replace(11, 17, listOf(inferred))

        verify(postPlaceTagRepository).deleteAllByPostIdAndPlaceId(11, 17)
        verify(postPlaceTagRepository).saveAll(anyList<PostPlaceTagEntity>())
        verify(place).updateRepresentativeTags(listOf(PlaceTag.QUIET, PlaceTag.SOLO_DINING))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-28T00:00:00Z")
    }
    private fun savedPostWithMemo(userId: Long, memo: String?): UserSavedPostEntity {
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(savedPost.id).thenReturn(if (userId == 7L) 21 else 22)
        `when`(savedPost.userId).thenReturn(userId)
        `when`(savedPost.memo).thenReturn(memo)
        return savedPost
    }
}
