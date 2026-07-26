package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.UpdatePostPlaceBookmarkPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdatePostPlaceBookmarkUseCaseTest {
    @Test
    fun `updates the bookmark without removing the associated place`() {
        var update: List<Any>? = null
        val useCase = UpdatePostPlaceBookmarkUseCase(
            UpdatePostPlaceBookmarkPort { userId, postId, placeId, bookmarked ->
                update = listOf(userId, postId, placeId, bookmarked)
                true
            },
        )

        useCase(UpdatePostPlaceBookmarkUseCase.Command(7, 11, 17, false))

        assertEquals(listOf(7L, 11L, 17L, false), update)
    }

    @Test
    fun `hides a missing or unauthorized association behind not found`() {
        val useCase = UpdatePostPlaceBookmarkUseCase(
            UpdatePostPlaceBookmarkPort { _, _, _, _ -> false },
        )

        assertFailsWith<PostNotFoundException> {
            useCase(UpdatePostPlaceBookmarkUseCase.Command(7, 11, 17, false))
        }
    }
}
