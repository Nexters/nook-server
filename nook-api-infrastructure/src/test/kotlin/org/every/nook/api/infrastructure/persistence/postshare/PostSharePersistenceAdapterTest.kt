package org.every.nook.api.infrastructure.persistence.postshare

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.share.PostShareException
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PostSharePersistenceAdapterTest {
    private val f = PostShareTestFixture()

    @Test
    fun `snapshot excludes personal fields and freezes selected places even after owner edits`() {
        val token = f.issue()
        assertTrue(PostShareTokens.valid(token))
        assertNotEquals(token, f.storedLink.tokenHash)
        val json = f.mapper.readTree(f.storedLink.snapshot)
        assertFalse(json.has("memo"))
        assertFalse(json.has("groups"))
        assertFalse(json.path("places").get(0).has("memo"))
        assertFalse(json.path("places").get(0).has("bookmarked"))
        assertFalse(f.storedLink.snapshot.contains("owner private"))
        `when`(f.details.findDetail(7, 11)).thenReturn(f.detail.copy(title = "changed", places = emptyList()))
        val public = f.adapter.find(token, null)
        assertEquals("shared title", public.post.title)
        assertEquals(listOf(31L, 32L), public.post.places.map { it.id })
        assertEquals(listOf(2, 7), public.post.places.map { it.sequence })
        assertEquals(null, public.viewerSavedPostId)
    }

    @Test
    fun `only owner can issue and unfinished content cannot be snapshotted`() {
        assertFailsWith<PostNotFoundException> { f.adapter.issue(8, 11) }
        `when`(
            f.details.findDetail(7, 11),
        ).thenReturn(f.detail.copy(processingStatus = PostProcessingStatusView.PENDING))
        assertFailsWith<PostShareException> { f.adapter.issue(7, 11) }
        verifyNoInteractions(f.links)
    }

    @Test
    fun `source deletion and restore permanently expire old links without touching recipient data`() {
        val token = f.issue()
        f.source.softDelete(PostShareTestFixture.NOW.plusSeconds(1))
        assertFailsWith<PostShareException> { f.adapter.find(token, null) }
        f.source.restore()
        assertFailsWith<PostShareException> { f.adapter.find(token, null) }
        assertFailsWith<PostShareException> { f.adapter.save(8, token, setOf(9)) }
        verifyNoInteractions(f.places, f.bookmarks)
    }

    @Test
    fun `new save copies only snapshot connections and freezes them against later parsing`() {
        val token = f.issue()
        val result = f.adapter.save(8, token, setOf(9))
        assertEquals(22, result.postId)
        assertFalse(result.alreadySaved)
        val copied = mockingDetails(f.places).invocations.single { it.method.name == "saveAll" }
            .getArgument<List<UserSavedPostPlaceEntity>>(0)
        assertEquals(listOf(31L, 32L), copied.map { it.placeId })
        assertEquals(listOf(2, 7), copied.map { it.sequence })
        assertTrue(copied.all { it.userSavedPostId == 22L })
        val saved = mockingDetails(f.saved).invocations.single { it.method.name == "saveAndFlush" }
            .getArgument<UserSavedPostEntity>(0)
        assertTrue(saved.placeLinksFrozen)
        assertEquals(null, saved.memo)
        verify(f.bookmarks).insertOrTouchWithMemo(8, 31, null)
        verify(f.sharedBookmarks).insertAllForActiveSubscribers(22, setOf(9))
    }

    @Test
    fun `existing save preserves private memo and places and only adds selected archive`() {
        val token = f.issue()
        val recipient = f.savedPost(8, 22).apply { memo = "my own memo" }
        `when`(f.locks.findByUserIdAndPostIdForUpdate(8, 101)).thenReturn(recipient)
        val result = f.adapter.save(8, token, setOf(9))
        assertTrue(result.alreadySaved)
        assertEquals(22, result.postId)
        assertEquals("my own memo", recipient.memo)
        assertFalse(recipient.placeLinksFrozen)
        verifyNoInteractions(f.places, f.bookmarks)
        verify(f.sharedBookmarks).insertAllForActiveSubscribers(22, setOf(9))
    }

    @Test
    fun `repeated save does not insert an archive connection already created by a preceding request`() {
        val token = f.issue()
        val recipient = f.savedPost(8, 22)
        `when`(f.locks.findByUserIdAndPostIdForUpdate(8, 101)).thenReturn(recipient)
        `when`(f.groupPosts.findAllByUserSavedPostIdForUpdate(22)).thenReturn(
            listOf(org.every.nook.api.infrastructure.persistence.group.GroupPostEntity(9, 22)),
        )
        assertTrue(f.adapter.save(8, token, setOf(9)).alreadySaved)
        val inserted = mockingDetails(f.groupPosts).invocations.single { it.method.name == "saveAll" }
            .getArgument<List<org.every.nook.api.infrastructure.persistence.group.GroupPostEntity>>(0)
        assertTrue(inserted.isEmpty())
        verifyNoInteractions(f.places, f.bookmarks)
    }

    @Test
    fun `restoring a deleted receiver uses snapshot instead of stale deleted connections`() {
        val token = f.issue()
        val recipient = f.savedPost(8, 22)
        `when`(f.saved.restoreByUserIdAndPostId(8, 101)).thenReturn(1)
        `when`(f.locks.findByUserIdAndPostIdForUpdate(8, 101)).thenReturn(recipient)
        assertFalse(f.adapter.save(8, token, setOf(9)).alreadySaved)
        assertTrue(recipient.placeLinksFrozen)
        verify(f.places).deleteAllByUserSavedPostId(22)
    }

    @Test
    fun `public viewer id points to receiver saved post rather than original id`() {
        val token = f.issue()
        `when`(f.saved.findByUserIdAndPostId(8, 101)).thenReturn(f.savedPost(8, 22))
        assertEquals(22, f.adapter.find(token, 8).viewerSavedPostId)
    }

    @Test
    fun `unowned archive and invalid token cannot persist a copy`() {
        val token = f.issue()
        assertFailsWith<GroupNotFoundException> { f.adapter.save(8, token, setOf(10)) }
        assertFailsWith<PostShareException> { f.adapter.find("invalid", null) }
        verifyNoInteractions(f.places, f.bookmarks, f.sharedBookmarks)
    }

    @Test
    fun `completed content with no places can be shared and saved without recrawling`() {
        `when`(f.details.findDetail(7, 11)).thenReturn(f.detail.copy(places = emptyList()))
        val token = f.issue()
        assertTrue(f.adapter.find(token, null).post.places.isEmpty())
        assertFalse(f.adapter.save(8, token, setOf(9)).alreadySaved)
        verifyNoInteractions(f.bookmarks)
    }
}
