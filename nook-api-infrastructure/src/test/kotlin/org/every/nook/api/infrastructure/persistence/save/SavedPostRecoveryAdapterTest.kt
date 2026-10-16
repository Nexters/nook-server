package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.admin.ParsingRecoveryError
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.processing.PostProcessingDispositionStore
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedPostRecoveryAdapterTest {
    private val saved = mock(UserSavedPostJpaRepository::class.java)
    private val groups = mock(GroupPostJpaRepository::class.java)
    private val content = mock(PostContentParsingJobJpaRepository::class.java)
    private val places = mock(PlaceParsingJobJpaRepository::class.java)
    private val disposition = mock(PostProcessingDispositionStore::class.java)
    private val now = Instant.parse("2026-10-17T00:00:00Z")
    private val adapter = SavedPostRecoveryAdapter(
        saved,
        groups,
        content,
        places,
        disposition,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val job = PostContentParsingJobEntity(
        101,
        PostContentParsingStatus.FAILED,
        "provider failed",
        4,
    ).apply {
        retryAttemptCount = 4
        lastFailedAt = now.minusSeconds(30)
    }

    init {
        `when`(saved.findByIdAndUserId(11, 7)).thenReturn(UserSavedPostEntity(7, 101, "memo"))
        `when`(content.findByPostIdForUpdate(101)).thenReturn(job)
        `when`(content.findByPostId(101)).thenReturn(job)
        `when`(groups.findActiveSavedPostIdsWithActiveGroup(listOf(11))).thenReturn(listOf(11))
    }

    @Test
    fun `recovery uses existing source and preserves saved post groups and failure history`() {
        val result = adapter.request(7, 11)
        assertTrue(result.requested)
        assertEquals(11, result.postId)
        assertEquals(PostProcessingStatusView.PENDING, result.processing.status)
        assertEquals(4, job.attemptCount)
        assertEquals(0, job.retryAttemptCount)
        assertEquals(now, job.nextAttemptAt)
        assertEquals("provider failed", job.failureReason)
        assertEquals(now.minusSeconds(30), job.lastFailedAt)
        verify(content).findByPostIdForUpdate(101)
        verify(disposition).requireOpen(101)
        verify(saved, never()).save(org.mockito.ArgumentMatchers.any(UserSavedPostEntity::class.java))
        verify(groups, never()).saveAll(org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `duplicate request while pending or processing does not restart execution`() {
        assertTrue(adapter.request(7, 11).requested)
        assertFalse(adapter.request(7, 11).requested)
        job.status = PostContentParsingStatus.PROCESSING
        job.attemptCount = 5
        job.retryAttemptCount = 1
        assertFalse(adapter.request(7, 11).requested)
        assertEquals(5, job.attemptCount)
        assertEquals(1, job.retryAttemptCount)
    }

    @Test
    fun `retry is allowed again after another failure`() {
        adapter.request(7, 11)
        job.status = PostContentParsingStatus.FAILED
        job.attemptCount = 8
        job.retryAttemptCount = 4
        assertTrue(adapter.request(7, 11).requested)
        assertEquals(8, job.attemptCount)
        assertEquals(0, job.retryAttemptCount)
    }

    @Test
    fun `another user or deleted saved post cannot request or inspect recovery`() {
        assertFailsWith<PostNotFoundException> { adapter.request(8, 11) }
        assertFailsWith<PostNotFoundException> { adapter.find(8, 11) }
        verifyNoInteractions(content, groups, disposition)
    }

    @Test
    fun `deleted archive stops recovery without resetting job`() {
        `when`(groups.findActiveSavedPostIdsWithActiveGroup(listOf(11))).thenReturn(emptyList())
        assertFailsWith<GroupNotFoundException> { adapter.request(7, 11) }
        assertEquals(PostContentParsingStatus.FAILED, job.status)
        assertEquals(4, job.retryAttemptCount)
        verifyNoInteractions(disposition)
    }

    @Test
    fun `closed processing disposition cannot be bypassed from the app`() {
        doThrow(ParsingRecoveryException(ParsingRecoveryError.DISPOSITION_CONFLICT))
            .`when`(disposition).requireOpen(101)
        assertFailsWith<ParsingRecoveryException> { adapter.request(7, 11) }
        assertEquals(PostContentParsingStatus.FAILED, job.status)
    }

    @Test
    fun `completed content with failed place recognition remains saved without user place retry`() {
        job.status = PostContentParsingStatus.COMPLETED
        `when`(places.findByPostId(101)).thenReturn(PlaceParsingJobEntity(101, status = PlaceParsingStatus.FAILED))
        val result = adapter.request(7, 11)
        assertFalse(result.requested)
        assertEquals(PostProcessingStatusView.COMPLETED, result.processing.status)
        verifyNoInteractions(groups, disposition)
    }

    @Test
    fun `status lookup does not enqueue jobs`() {
        assertEquals(PostProcessingStatusView.FAILED, adapter.find(7, 11).processing.status)
        verify(content, never()).findByPostIdForUpdate(101)
        verifyNoInteractions(groups, disposition)
    }
}
