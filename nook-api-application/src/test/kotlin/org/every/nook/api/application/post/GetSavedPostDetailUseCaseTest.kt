package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GetSavedPostDetailUseCaseTest {
    @Test
    fun `post owned by another user is exposed as not found`() {
        val useCase = GetSavedPostDetailUseCase(EmptySavedPostQueryPort)

        assertFailsWith<PostNotFoundException> {
            useCase(GetSavedPostDetailUseCase.Query(userId = 7, postId = 11))
        }
    }

    private object EmptySavedPostQueryPort : SavedPostQueryPort {
        override fun findAll(userId: Long, page: Int, size: Int): SavedPostPage = error("not used")

        override fun findDetail(userId: Long, postId: Long): SavedPostDetail? = null
    }
}
