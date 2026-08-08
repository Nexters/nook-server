package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.SearchAllStoredPlacesPort
import org.every.nook.api.application.place.port.SearchMyStoredPlacesPort
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SearchStoredPlacesUseCaseTest {
    @Test
    fun `searches all places with trimmed keyword and one extra row`() {
        var captured: List<Any> = emptyList()
        val useCase = SearchAllStoredPlacesUseCase(
            SearchAllStoredPlacesPort { userId, keyword, offset, limit ->
                captured = listOf(userId, keyword, offset, limit)
                listOf(place(1), place(2), place(3))
            },
        )

        val result = useCase(SearchAllStoredPlacesUseCase.Query(7, "  용산  ", 1, 2))

        assertEquals(listOf(7L, "용산", 2, 3), captured)
        assertEquals(listOf(1L, 2L), result.items.map { it.id })
        assertTrue(result.hasNext)
    }

    @Test
    fun `searches only my places`() {
        val useCase = SearchMyStoredPlacesUseCase(
            SearchMyStoredPlacesPort { userId, keyword, offset, limit ->
                assertEquals(listOf(7L, "카페", 0, 21), listOf(userId, keyword, offset, limit))
                listOf(place(1, bookmarked = true))
            },
        )

        val result = useCase(SearchMyStoredPlacesUseCase.Query(7, "카페", 0, 20))

        assertEquals(1, result.items.size)
        assertTrue(result.items.single().bookmarked)
    }

    @Test
    fun `rejects blank keyword`() {
        val useCase = SearchAllStoredPlacesUseCase(SearchAllStoredPlacesPort { _, _, _, _ -> emptyList() })

        assertFailsWith<IllegalArgumentException> {
            useCase(SearchAllStoredPlacesUseCase.Query(7, " ", 0, 20))
        }
    }

    private fun place(id: Long, bookmarked: Boolean = false): StoredPlaceSearchView = StoredPlaceSearchView(
        id = id,
        name = "장소$id",
        address = "서울",
        category = "카페",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        thumbnailUrl = null,
        tags = emptyList(),
        bookmarked = bookmarked,
    )
}
