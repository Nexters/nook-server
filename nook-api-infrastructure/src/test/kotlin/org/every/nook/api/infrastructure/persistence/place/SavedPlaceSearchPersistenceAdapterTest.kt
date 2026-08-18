package org.every.nook.api.infrastructure.persistence.place

import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SavedPlaceSearchPersistenceAdapterTest {
    private val repository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val adapter = SavedPlaceSearchPersistenceAdapter(repository)

    @Test
    fun `maps a saved place page and escapes like wildcards`() {
        val row = mock(SavedPlaceSearchProjection::class.java)
        val group = mock(SavedPlaceSearchGroupProjection::class.java)
        val pageable = PageRequest.of(1, 2)
        `when`(row.id).thenReturn(17)
        `when`(row.name).thenReturn("카페 100%_!")
        `when`(row.address).thenReturn("서울 성동구")
        `when`(row.category).thenReturn("카페")
        `when`(group.id).thenReturn(9)
        `when`(group.name).thenReturn("카페 모음")
        `when`(group.color).thenReturn("MINT")
        `when`(group.matchedPlaceCount).thenReturn(3)
        `when`(repository.searchSavedPlaces(7, "%100!%!_!!%", 9, pageable)).thenReturn(
            PageImpl(listOf(row), pageable, 3),
        )
        `when`(repository.findSavedPlaceSearchGroups(7, "%100!%!_!!%")).thenReturn(listOf(group))

        val result = adapter.search(userId = 7, keyword = "100%_!", groupId = 9, page = 1, size = 2)

        assertEquals(17, result.items.single().id)
        assertEquals("카페 100%_!", result.items.single().name)
        assertEquals("서울 성동구", result.items.single().address)
        assertEquals("카페", result.items.single().category)
        assertEquals(9, result.groups.single().id)
        assertEquals("카페 모음", result.groups.single().name)
        assertEquals("MINT", result.groups.single().color)
        assertEquals(3, result.groups.single().matchedPlaceCount)
        assertEquals(3, result.totalElements)
        assertEquals(2, result.totalPages)
        assertFalse(result.hasNext)
        verify(repository).searchSavedPlaces(7, "%100!%!_!!%", 9, pageable)
        verify(repository).findSavedPlaceSearchGroups(7, "%100!%!_!!%")
    }
}
