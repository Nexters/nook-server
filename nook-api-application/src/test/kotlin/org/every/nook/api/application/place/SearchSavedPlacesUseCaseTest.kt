package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.SavedPlaceSearchPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchSavedPlacesUseCaseTest {
    @Test
    fun `trims keyword and searches only the requested users places`() {
        val expected = SavedPlaceSearchPageView(
            items = listOf(SavedPlaceSearchItemView(17, "카페 누크", "서울 성동구", "카페")),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1,
            hasNext = false,
        )
        val useCase = SearchSavedPlacesUseCase(
            SavedPlaceSearchPort { userId, keyword, page, size ->
                assertEquals(7, userId)
                assertEquals("카페", keyword)
                assertEquals(0, page)
                assertEquals(20, size)
                expected
            },
        )

        val result = useCase(SearchSavedPlacesUseCase.Query(7, "  카페  ", 0, 20))

        assertEquals(expected, result)
    }

    @Test
    fun `rejects blank keyword and invalid pagination`() {
        val useCase = SearchSavedPlacesUseCase { _, _, _, _ -> error("must not search") }

        assertFailsWith<InvalidPlaceSearchRequestException> {
            useCase(SearchSavedPlacesUseCase.Query(7, " ", 0, 20))
        }
        assertFailsWith<InvalidPlaceSearchRequestException> {
            useCase(SearchSavedPlacesUseCase.Query(7, "카페", -1, 20))
        }
        assertFailsWith<InvalidPlaceSearchRequestException> {
            useCase(SearchSavedPlacesUseCase.Query(7, "카페", 0, 101))
        }
    }
}
