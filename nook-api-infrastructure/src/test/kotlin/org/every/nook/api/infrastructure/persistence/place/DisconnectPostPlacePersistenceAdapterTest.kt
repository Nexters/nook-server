package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.port.DisconnectPostPlacePort
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class DisconnectPostPlacePersistenceAdapterTest {
    private val savedPostRepository = mock(UserSavedPostLockJpaRepository::class.java)
    private val savedPostPlaceRepository = mock(UserSavedPostPlaceJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val adapter = DisconnectPostPlacePersistenceAdapter(
        savedPostRepository,
        savedPostPlaceRepository,
        bookmarkRepository,
    )

    @Test
    fun `deletes only the requested saved post relation`() {
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7))
            .thenReturn(UserSavedPostEntity(userId = 7, postId = 101))
        `when`(savedPostPlaceRepository.deleteByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(1)
        `when`(savedPostPlaceRepository.existsActiveByUserIdAndPlaceId(7, 17)).thenReturn(1)

        assertEquals(
            DisconnectPostPlacePort.Result.DISCONNECTED,
            adapter.disconnect(userId = 7, savedPostId = 11, placeId = 17),
        )

        verify(savedPostPlaceRepository).deleteByUserSavedPostIdAndPlaceId(11, 17)
        verify(bookmarkRepository, never()).deleteByUserIdAndPlaceId(7, 17)
    }

    @Test
    fun `removes bookmark when no saved post still relates to the place`() {
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7))
            .thenReturn(UserSavedPostEntity(userId = 7, postId = 101))
        `when`(savedPostPlaceRepository.deleteByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(1)
        `when`(savedPostPlaceRepository.existsActiveByUserIdAndPlaceId(7, 17)).thenReturn(0)

        assertEquals(
            DisconnectPostPlacePort.Result.DISCONNECTED,
            adapter.disconnect(userId = 7, savedPostId = 11, placeId = 17),
        )

        verify(bookmarkRepository).deleteByUserIdAndPlaceId(7, 17)
    }

    @Test
    fun `does not expose or change another users saved post`() {
        `when`(savedPostRepository.findByIdAndUserIdForUpdate(11, 7)).thenReturn(null)

        assertEquals(
            DisconnectPostPlacePort.Result.POST_NOT_FOUND,
            adapter.disconnect(userId = 7, savedPostId = 11, placeId = 17),
        )

        verifyNoInteractions(savedPostPlaceRepository, bookmarkRepository)
    }
}
