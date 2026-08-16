package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectPostPlacePersistenceAdapterTest {
    private val savedPostRepository = mock(UserSavedPostLockJpaRepository::class.java)
    private val placeRepository = mock(PlaceJpaRepository::class.java)
    private val savedPostPlaceRepository = mock(UserSavedPostPlaceJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val parsingJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val adapter = ConnectPostPlacePersistenceAdapter(
        savedPostRepository,
        placeRepository,
        savedPostPlaceRepository,
        bookmarkRepository,
        parsingJobRepository,
        eventPublisher,
    )

    @Test
    fun `hides a saved post owned by another user`() {
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(null)

        assertEquals(
            ConnectPostPlacePort.Result.PostNotFound,
            adapter.connect(7, 11, candidate(), null),
        )
        verifyNoInteractions(placeRepository, savedPostPlaceRepository, bookmarkRepository)
    }

    @Test
    fun `rejects connection while place parsing is in progress`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.PROCESSING)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)

        assertEquals(
            ConnectPostPlacePort.Result.ParsingInProgress,
            adapter.connect(7, 11, candidate(), null),
        )
        verifyNoInteractions(placeRepository, savedPostPlaceRepository, bookmarkRepository)
    }

    @Test
    fun `creates a missing user place link and bookmark without changing the shared failed job`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.FAILED)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)
        `when`(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "1234")).thenReturn(place)
        `when`(savedPostPlaceRepository.findByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(null)
        `when`(savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(11))
            .thenReturn(listOf(UserSavedPostPlaceEntity(11, 16, 0)))

        assertEquals(
            ConnectPostPlacePort.Result.Connected(17),
            adapter.connect(
                7,
                11,
                candidate(),
                PlaceSupplement(null, listOf("https://cdn.example.com/google-place.jpg")),
            ),
        )

        verify(place).updateSupplement(PlaceSupplement(null, listOf("https://cdn.example.com/google-place.jpg")))
        verify(placeRepository).insertIgnore(
            provider = "KAKAO",
            externalPlaceId = "1234",
            name = "퍼머넌트해비탯",
            address = "경기 용인시",
            city = "용인",
            latitude = BigDecimal("37.5"),
            longitude = BigDecimal("127.0"),
            category = "카페",
            phoneNumber = null,
        )
        val captor = ArgumentCaptor.forClass(UserSavedPostPlaceEntity::class.java)
        verify(savedPostPlaceRepository).save(captor.capture())
        assertEquals(11, captor.value.userSavedPostId)
        assertEquals(17, captor.value.placeId)
        assertEquals(1, captor.value.sequence)
        verify(bookmarkRepository).insertIgnoreWithMemo(7, 17, "게시물 메모")
        assertEquals(PlaceParsingStatus.FAILED, job.status)
        assertEquals("failed", job.failureReason)
    }

    @Test
    fun `reuses an existing user place link idempotently`() {
        val savedPost = savedPost()
        val job = parsingJob(PlaceParsingStatus.COMPLETED)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(savedPost)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(job)
        `when`(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "1234")).thenReturn(place)
        `when`(savedPostPlaceRepository.findByUserSavedPostIdAndPlaceId(11, 17))
            .thenReturn(UserSavedPostPlaceEntity(11, 17, 0))

        assertEquals(
            ConnectPostPlacePort.Result.Connected(17),
            adapter.connect(7, 11, candidate(), null),
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
