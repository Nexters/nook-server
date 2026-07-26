package org.every.nook.api.application.save

import org.every.nook.api.application.save.error.SavedPostNotFoundException
import org.every.nook.api.application.save.port.UpdateSavedPostPlaceBookmarkPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateSavedPostPlaceBookmarkUseCaseTest {
    @Test
    fun `updates the bookmark without removing the associated place`() {
        var update: List<Any>? = null
        val useCase = UpdateSavedPostPlaceBookmarkUseCase(
            UpdateSavedPostPlaceBookmarkPort { userId, savedPostId, placeId, bookmarked ->
                update = listOf(userId, savedPostId, placeId, bookmarked)
                true
            },
        )

        useCase(UpdateSavedPostPlaceBookmarkUseCase.Command(7, 11, 17, false))

        assertEquals(listOf(7L, 11L, 17L, false), update)
    }

    @Test
    fun `hides a missing or unauthorized association behind not found`() {
        val useCase = UpdateSavedPostPlaceBookmarkUseCase(
            UpdateSavedPostPlaceBookmarkPort { _, _, _, _ -> false },
        )

        assertFailsWith<SavedPostNotFoundException> {
            useCase(UpdateSavedPostPlaceBookmarkUseCase.Command(7, 11, 17, false))
        }
    }
}
