package org.every.nook.api.infrastructure.persistence.place

import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceBookmarkPersistenceAdapterTest {
    private val repository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val adapter = PlaceBookmarkPersistenceAdapter(repository)

    @Test
    fun `bookmark identity uses only user and place`() {
        `when`(repository.isAccessible(7, 17)).thenReturn(1L)

        val updated = adapter.update(userId = 7, placeId = 17, bookmarked = true)

        assertTrue(updated)
        verify(repository).insertIgnore(7, 17)
    }

    @Test
    fun `unbookmark removes the place from the user bookmark set`() {
        `when`(repository.isAccessible(7, 17)).thenReturn(1L)

        val updated = adapter.update(userId = 7, placeId = 17, bookmarked = false)

        assertTrue(updated)
        verify(repository).deleteByUserIdAndPlaceId(7, 17)
    }

    @Test
    fun `inaccessible place is not changed`() {
        `when`(repository.isAccessible(7, 17)).thenReturn(0L)

        val updated = adapter.update(userId = 7, placeId = 17, bookmarked = true)

        assertFalse(updated)
        verify(repository, never()).insertIgnore(7, 17)
    }
}
