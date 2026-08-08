package org.every.nook.api.infrastructure.persistence.place

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoredPlaceSearchPersistenceAdapterTest {
    private val repository = mock(StoredPlaceSearchJpaRepository::class.java)
    private val adapter = StoredPlaceSearchPersistenceAdapter(repository, jacksonObjectMapper())

    @Test
    fun `maps all place search result and representative tags`() {
        val projection = projection(bookmarked = false)
        `when`(repository.searchAll(7, "용산", 20, 21)).thenReturn(listOf(projection))

        val result = adapter.searchAll(7, "용산", 20, 21)

        assertEquals("원동미나리삼겹살", result.single().name)
        assertEquals(listOf("조용한", "혼밥"), result.single().tags)
        assertEquals(false, result.single().bookmarked)
        verify(repository).searchAll(7, "용산", 20, 21)
    }

    @Test
    fun `returns only repository results for my place search`() {
        val projection = projection(bookmarked = true)
        `when`(repository.searchMine(7, "용산", 0, 21)).thenReturn(listOf(projection))

        val result = adapter.searchMine(7, "용산", 0, 21)

        assertTrue(result.single().bookmarked)
        verify(repository).searchMine(7, "용산", 0, 21)
    }

    private fun projection(bookmarked: Boolean): StoredPlaceSearchProjection = object : StoredPlaceSearchProjection {
        override val id = 17L
        override val name = "원동미나리삼겹살"
        override val address = "서울 용산구"
        override val category = "한식"
        override val latitude = BigDecimal("37.5")
        override val longitude = BigDecimal("127.0")
        override val thumbnailUrl: String? = null
        override val representativeTags = "[\"QUIET\",\"SOLO_DINING\"]"
        override val bookmarked = bookmarked
    }
}
