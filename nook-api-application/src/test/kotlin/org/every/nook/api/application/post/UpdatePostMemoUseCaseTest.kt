package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.UpdatePostMemoPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdatePostMemoUseCaseTest {
    @Test
    fun `updates the memo owned by the user`() {
        var updated: Triple<Long, Long, String?>? = null
        val useCase = UpdatePostMemoUseCase(
            UpdatePostMemoPort { userId, postId, memo ->
                updated = Triple(userId, postId, memo)
                true
            },
        )

        useCase(UpdatePostMemoUseCase.Command(userId = 7, postId = 11, memo = "평일에 방문"))

        assertEquals(Triple(7L, 11L, "평일에 방문"), updated)
    }

    @Test
    fun `deletes the memo with null`() {
        var updatedMemo: String? = "기존 메모"
        val useCase = UpdatePostMemoUseCase(
            UpdatePostMemoPort { _, _, memo ->
                updatedMemo = memo
                true
            },
        )

        useCase(UpdatePostMemoUseCase.Command(userId = 7, postId = 11, memo = null))

        assertEquals(null, updatedMemo)
    }

    @Test
    fun `throws not found when the user does not own the saved post`() {
        val useCase = UpdatePostMemoUseCase(UpdatePostMemoPort { _, _, _ -> false })

        assertFailsWith<PostNotFoundException> {
            useCase(UpdatePostMemoUseCase.Command(userId = 8, postId = 11, memo = "침범"))
        }
    }
}
