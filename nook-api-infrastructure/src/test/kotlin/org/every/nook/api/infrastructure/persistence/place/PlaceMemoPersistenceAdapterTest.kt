package org.every.nook.api.infrastructure.persistence.place

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceMemoPersistenceAdapterTest {
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val adapter = PlaceMemoPersistenceAdapter(bookmarkRepository)

    @Test
    fun `writes the memo onto the user's bookmark`() {
        val bookmark = UserPlaceBookmarkEntity(userId = 7, placeId = 17)
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(bookmark)

        assertTrue(adapter.update(userId = 7, placeId = 17, memo = "주차 어려움"))

        assertEquals("주차 어려움", bookmark.memo)
    }

    @Test
    fun `clears the memo when null is given`() {
        val bookmark = UserPlaceBookmarkEntity(userId = 7, placeId = 17, memo = "예전 메모")
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(bookmark)

        assertTrue(adapter.update(userId = 7, placeId = 17, memo = null))

        assertNull(bookmark.memo)
    }

    @Test
    fun `fails when the place is not bookmarked by the user`() {
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(null)

        assertFalse(adapter.update(userId = 7, placeId = 17, memo = "메모"))
    }
}
