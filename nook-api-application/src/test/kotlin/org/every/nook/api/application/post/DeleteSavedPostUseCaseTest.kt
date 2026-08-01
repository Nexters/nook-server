package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.DeleteSavedPostPort
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DeleteSavedPostUseCaseTest {
    @Test
    fun `deletes owned saved post`() {
        var command: Pair<Long, Long>? = null
        val useCase = DeleteSavedPostUseCase { userId, postId ->
            command = userId to postId
            true
        }

        useCase(DeleteSavedPostUseCase.Command(7, 11))

        kotlin.test.assertEquals(7L to 11L, command)
    }

    @Test
    fun `missing saved post throws not found`() {
        val useCase = DeleteSavedPostUseCase(DeleteSavedPostPort { _, _ -> false })

        assertFailsWith<PostNotFoundException> {
            useCase(DeleteSavedPostUseCase.Command(7, 11))
        }
    }
}
