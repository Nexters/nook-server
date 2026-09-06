package org.every.nook.api.application.post

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.SavedPostQueryPort
import kotlin.test.Test
import kotlin.test.assertEquals

class ListSavedPostsUseCaseTest {
    @Test
    fun `user and pagination are passed to query port`() {
        var captured: List<Int>? = null
        val port = object : SavedPostQueryPort {
            override fun findAll(userId: Long, page: Int, size: Int): SavedPostPage {
                captured = listOf(userId.toInt(), page, size)
                return SavedPostPage(emptyList(), page, size, 0, 0, false)
            }

            override fun findDetail(userId: Long, postId: Long): SavedPostDetail? = error("not used")
        }

        ListSavedPostsUseCase(port)(ListSavedPostsUseCase.Query(userId = 7, page = 2, size = 10))

        assertEquals(listOf(7, 2, 10), captured)
    }

    @Test
    fun `records an archive view only for the first page`() {
        val events = mutableListOf<UserAnalyticsEventName>()
        val port = object : SavedPostQueryPort {
            override fun findAll(userId: Long, page: Int, size: Int) =
                SavedPostPage(emptyList(), page, size, 0, 0, false)

            override fun findDetail(userId: Long, postId: Long): SavedPostDetail? = error("not used")
        }
        val useCase = ListSavedPostsUseCase(
            port,
            UserAnalyticsEventRecorder { record -> events += record.eventName },
        )

        useCase(ListSavedPostsUseCase.Query(userId = 7, page = 0, size = 10))
        useCase(ListSavedPostsUseCase.Query(userId = 7, page = 1, size = 10))

        assertEquals(listOf(UserAnalyticsEventName.ARCHIVE_VIEW), events)
    }
}
