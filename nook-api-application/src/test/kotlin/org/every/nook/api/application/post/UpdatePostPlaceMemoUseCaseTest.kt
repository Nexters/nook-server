package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.UpdatePostPlaceMemoPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdatePostPlaceMemoUseCaseTest {
    @Test
    fun `updates a place memo owned by the user`() {
        var updated: CommandValue? = null
        val useCase = UpdatePostPlaceMemoUseCase(
            UpdatePostPlaceMemoPort { userId, postId, placeId, memo ->
                updated = CommandValue(userId, postId, placeId, memo)
                true
            },
        )

        useCase(
            UpdatePostPlaceMemoUseCase.Command(userId = 7, postId = 11, placeId = 17, memo = "창가 자리 좋음"),
        )

        assertEquals(CommandValue(7, 11, 17, "창가 자리 좋음"), updated)
    }

    @Test
    fun `deletes a place memo with null`() {
        var updatedMemo: String? = "기존 장소 메모"
        val useCase = UpdatePostPlaceMemoUseCase(
            UpdatePostPlaceMemoPort { _, _, _, memo ->
                updatedMemo = memo
                true
            },
        )

        useCase(UpdatePostPlaceMemoUseCase.Command(userId = 7, postId = 11, placeId = 17, memo = null))

        assertEquals(null, updatedMemo)
    }

    @Test
    fun `throws not found when the user cannot update the saved post place`() {
        val useCase = UpdatePostPlaceMemoUseCase(UpdatePostPlaceMemoPort { _, _, _, _ -> false })

        assertFailsWith<PostNotFoundException> {
            useCase(UpdatePostPlaceMemoUseCase.Command(userId = 8, postId = 11, placeId = 17, memo = "침범"))
        }
    }

    private data class CommandValue(val userId: Long, val postId: Long, val placeId: Long, val memo: String?)
}
