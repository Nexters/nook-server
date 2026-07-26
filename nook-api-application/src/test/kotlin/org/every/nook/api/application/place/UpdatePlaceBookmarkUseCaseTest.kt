package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdatePlaceBookmarkUseCaseTest {
    @Test
    fun `updates a user place bookmark without a post identifier`() {
        var update: List<Any>? = null
        val useCase = UpdatePlaceBookmarkUseCase(
            UpdatePlaceBookmarkPort { userId, placeId, bookmarked ->
                update = listOf(userId, placeId, bookmarked)
                true
            },
        )

        useCase(UpdatePlaceBookmarkUseCase.Command(userId = 7, placeId = 17, bookmarked = false))

        assertEquals(listOf(7L, 17L, false), update)
    }

    @Test
    fun `fails when the place is not available to the user`() {
        val useCase = UpdatePlaceBookmarkUseCase(
            UpdatePlaceBookmarkPort { _, _, _ -> false },
        )

        assertFailsWith<PlaceNotFoundException> {
            useCase(UpdatePlaceBookmarkUseCase.Command(userId = 7, placeId = 17, bookmarked = true))
        }
    }
}
