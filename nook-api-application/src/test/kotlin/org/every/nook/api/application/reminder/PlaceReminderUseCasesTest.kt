package org.every.nook.api.application.reminder

import org.every.nook.api.application.post.ListUnlinkedSavedPostsUseCase
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.port.UnlinkedSavedPostQueryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceReminderUseCasesTest {
    @Test
    fun `banner follows both preference and current actionable count while OFF keeps count visible`() {
        val preferences = Preferences()
        val posts = Posts()
        val get = GetPlaceReminderUseCase(preferences, posts)
        val update = UpdatePlaceReminderUseCase(preferences, posts)
        assertTrue(get(GetPlaceReminderUseCase.Query(7)).showBanner)
        val disabled = update(UpdatePlaceReminderUseCase.Command(7, false))
        assertFalse(disabled.enabled)
        assertFalse(disabled.showBanner)
        assertEquals(2L, disabled.unlinkedPostCount)
        assertFalse(get(GetPlaceReminderUseCase.Query(7)).showBanner)
        assertTrue(get(GetPlaceReminderUseCase.Query(8)).enabled)
        assertTrue(update(UpdatePlaceReminderUseCase.Command(7, true)).showBanner)
        posts.count = 0
        assertFalse(get(GetPlaceReminderUseCase.Query(7)).showBanner)
    }

    @Test
    fun `list passes authenticated ownership and pagination and rejects invalid requests`() {
        val posts = Posts()
        val list = ListUnlinkedSavedPostsUseCase(posts)
        assertEquals(posts.page, list(ListUnlinkedSavedPostsUseCase.Query(7, 1, 20)))
        assertEquals(Triple(7L, 1, 20), posts.request)
        listOf(-1 to 20, 0 to 0, 0 to 101).forEach { (page, size) ->
            assertFailsWith<IllegalArgumentException> { list(ListUnlinkedSavedPostsUseCase.Query(7, page, size)) }
        }
    }

    private class Preferences : PlaceReminderPreferencePort {
        private val values = mutableMapOf<Long, Boolean>()
        override fun isEnabled(userId: Long): Boolean = values[userId] ?: true
        override fun update(userId: Long, enabled: Boolean): Boolean {
            values[userId] = enabled
            return enabled
        }
    }

    private class Posts : UnlinkedSavedPostQueryPort {
        var count = 2L
        var request: Triple<Long, Int, Int>? = null
        val page = SavedPostPage(emptyList(), 1, 20, 0, 0, false)
        override fun countUnlinked(userId: Long): Long = count
        override fun findUnlinked(userId: Long, page: Int, size: Int): SavedPostPage {
            request = Triple(userId, page, size)
            return this.page
        }
    }
}
