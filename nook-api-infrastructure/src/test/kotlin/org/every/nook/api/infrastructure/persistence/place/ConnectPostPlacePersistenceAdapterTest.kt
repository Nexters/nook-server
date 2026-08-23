package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectPostPlacePersistenceAdapterTest {
    private val savedPostRepository = mock(UserSavedPostLockJpaRepository::class.java)
    private val placeIdentityResolver = mock(PlaceIdentityResolver::class.java)
    private val savedPostPlaceRepository = mock(UserSavedPostPlaceJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val sharedBookmarkSyncRepository = mock(SharedPlaceBookmarkSyncJpaRepository::class.java)
    private val parsingJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val followUpJobPort = mock(ParsingFollowUpJobPort::class.java)
    private val adapter = ConnectPostPlacePersistenceAdapter(
        savedPostRepository,
        placeIdentityResolver,
        savedPostPlaceRepository,
        bookmarkRepository,
        sharedBookmarkSyncRepository,
        parsingJobRepository,
        followUpJobPort,
    )

    @Test
    fun `hides a saved post owned by another user`() {
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(null)

        assertEquals(
            ConnectPostPlacePort.Result.PostNotFound,
            adapter.connect(7, 11, candidate()),
        )
        verifyNoInteractions(placeIdentityResolver, savedPostPlaceRepository, bookmarkRepository)
    }

    @Test
    fun `rejects connection while place parsing is in progress`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.PROCESSING)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)

        assertEquals(
            ConnectPostPlacePort.Result.ParsingInProgress,
            adapter.connect(7, 11, candidate()),
        )
        verifyNoInteractions(placeIdentityResolver, savedPostPlaceRepository, bookmarkRepository)
    }

    @Test
    fun `creates a missing user place link and bookmark without changing the shared failed job`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.FAILED)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)
        `when`(placeIdentityResolver.resolve(candidate())).thenReturn(place)
        `when`(place.shouldRequestThumbnailSupplement()).thenReturn(true)
        `when`(savedPostPlaceRepository.findByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(null)
        `when`(savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(11))
            .thenReturn(listOf(UserSavedPostPlaceEntity(11, 16, 0)))

        assertEquals(ConnectPostPlacePort.Result.Connected(17), adapter.connect(7, 11, candidate()))

        verify(place).updateThumbnailParsing(PlaceThumbnailParsingStatus.PENDING, null)
        verify(placeIdentityResolver).resolve(candidate())
        verify(sharedBookmarkSyncRepository).insertForActiveSubscribers(savedPostId = 11, placeId = 17)
        val captor = ArgumentCaptor.forClass(UserSavedPostPlaceEntity::class.java)
        verify(savedPostPlaceRepository).save(captor.capture())
        assertEquals(11, captor.value.userSavedPostId)
        assertEquals(17, captor.value.placeId)
        assertEquals(1, captor.value.sequence)
        verify(bookmarkRepository).insertIgnoreWithMemo(7, 17, "게시물 메모")
        val followUps = followUps()
        val thumbnailEvent = followUps.filterIsInstance<PlaceThumbnailsRequestedEvent>().single()
        assertEquals(101, thumbnailEvent.postId)
        assertEquals(listOf(candidate()), thumbnailEvent.requests.map { it.place })
        assertEquals(1, followUps.filterIsInstance<PlaceTagsRequestedEvent>().size)
        assertEquals(PlaceParsingStatus.FAILED, job.status)
        assertEquals("failed", job.failureReason)
    }

    @Test
    fun `keeps a completed thumbnail when directly connecting an existing place`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.COMPLETED)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(place.shouldRequestThumbnailSupplement()).thenReturn(false)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)
        `when`(placeIdentityResolver.resolve(candidate())).thenReturn(place)
        `when`(savedPostPlaceRepository.findByUserSavedPostIdAndPlaceId(11, 17))
            .thenReturn(UserSavedPostPlaceEntity(11, 17, 0))

        assertEquals(ConnectPostPlacePort.Result.Connected(17), adapter.connect(7, 11, candidate()))

        verify(place, never()).updateThumbnailParsing(PlaceThumbnailParsingStatus.PENDING, null)
        assertEquals(1, followUps().filterIsInstance<PlaceTagsRequestedEvent>().size)
        assertEquals(0, followUps().filterIsInstance<PlaceThumbnailsRequestedEvent>().size)
    }

    @Test
    fun `reuses an existing user place link idempotently`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.COMPLETED)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)
        `when`(placeIdentityResolver.resolve(candidate())).thenReturn(place)
        `when`(savedPostPlaceRepository.findByUserSavedPostIdAndPlaceId(11, 17))
            .thenReturn(UserSavedPostPlaceEntity(11, 17, 0))

        assertEquals(
            ConnectPostPlacePort.Result.Connected(17),
            adapter.connect(7, 11, candidate()),
        )

        verify(savedPostPlaceRepository, never()).save(org.mockito.ArgumentMatchers.any())
        verify(bookmarkRepository).insertIgnoreWithMemo(7, 17, "게시물 메모")
    }

    private fun savedPost(): UserSavedPostEntity {
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(savedPost.id).thenReturn(11)
        `when`(savedPost.postId).thenReturn(101)
        `when`(savedPost.memo).thenReturn("게시물 메모")
        return savedPost
    }

    private fun parsingJob(status: PlaceParsingStatus): PlaceParsingJobEntity =
        PlaceParsingJobEntity(postId = 101, status = status, failureReason = "failed")

    private fun followUps(): List<Any> = mockingDetails(followUpJobPort).invocations
        .map { invocation -> invocation.arguments.single() }

    private fun candidate(): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1234",
        name = "퍼머넌트해비탯",
        address = "경기 용인시",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        category = "카페",
        phoneNumber = null,
        providerUrl = null,
    )
}
