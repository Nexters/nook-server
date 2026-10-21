package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.AdminPostRecoveryPort
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RefetchPostContentCommand
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminPostContentRefetchAdapterTest {
    private val content = mock(PostContentParsingJobJpaRepository::class.java)
    private val places = mock(PlaceParsingJobJpaRepository::class.java)
    private val followUps = mock(ParsingFollowUpJobJpaRepository::class.java)
    private val posts = mock(PostJpaRepository::class.java)
    private val caches = mock(ScrapingProviderResponseJpaRepository::class.java)
    private val disposition = mock(PostProcessingDispositionStore::class.java)
    private val audit = mock(AdminAuditLogPort::class.java)
    private val recovery = mock(AdminPostRecoveryPort::class.java)
    private val now = Instant.parse("2026-10-21T00:00:00Z")
    private val adapter = AdminPostContentRefetchAdapter(
        content, places, followUps, posts, caches, disposition,
        audit, recovery, Clock.fixed(now, ZoneOffset.UTC),
    )
    private val job = PostContentParsingJobEntity(101, PostContentParsingStatus.COMPLETED, attemptCount = 4)
    private val place = PlaceParsingJobEntity(101, PlaceParsingStatus.COMPLETED, textPlaceClues = "old")
    private val post = PostEntity("INSTAGRAM", "ABC123", "https://www.instagram.com/p/ABC123/", body = "old")
    private val command = RefetchPostContentCommand(101, AdminActor("operator", "ops@example.com"), "빈 수집 복구")

    init {
        `when`(content.findByPostIdForUpdate(101)).thenReturn(job)
        `when`(places.findByPostIdForUpdate(101)).thenReturn(place)
        `when`(posts.findById(101)).thenReturn(Optional.of(post))
        `when`(recovery.find(101)).thenReturn(AdminPostRecovery(101, emptyList()))
        `when`(caches.deleteInstagramResponses("ABC123")).thenReturn(2)
    }

    @Test
    fun `completed content is scheduled with its attempt history and old content preserved`() {
        adapter.refetch(command)
        assertEquals(PostContentParsingStatus.PENDING, job.status)
        assertEquals(4, job.attemptCount)
        assertEquals(0, job.retryAttemptCount)
        assertEquals(now, job.nextAttemptAt)
        assertEquals("old", post.body)
        assertEquals("old", place.textPlaceClues)
        verify(caches).deleteInstagramResponses("ABC123")
        val entry = mockingDetails(audit).invocations.single().getArgument<AdminAuditLogPort.Entry>(0)
        assertEquals(command.actor, entry.actor)
        assertEquals(command.reason, entry.reason)
        assertEquals("POST_CONTENT_REFETCH_REQUESTED", entry.action)
        kotlin.test.assertContains(entry.afterValue.orEmpty(), "\"removedCaches\":2")
    }

    @Test
    fun `failed content can be recollected but duplicate and active content cannot`() {
        job.status = PostContentParsingStatus.FAILED
        adapter.refetch(command)
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        job.status = PostContentParsingStatus.PROCESSING
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
    }

    @Test
    fun `active downstream work blocks refetch before cache mutation`() {
        for (status in listOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)) {
            place.status = status
            assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        }
        place.status = PlaceParsingStatus.COMPLETED
        for (status in listOf(ParsingFollowUpJobStatus.PENDING, ParsingFollowUpJobStatus.PROCESSING)) {
            `when`(followUps.findByPostIdForUpdate(101)).thenReturn(
                listOf(
                    ParsingFollowUpJobEntity(
                        ParsingFollowUpJobType.POST_MEDIA,
                        101,
                        "{}",
                        status = status,
                        nextAttemptAt = now,
                    ),
                ),
            )
            assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        }
        verifyNoInteractions(caches, audit)
    }

    @Test
    fun `non Instagram and missing posts never evict provider responses`() {
        `when`(posts.findById(101)).thenReturn(Optional.of(PostEntity("SHARED", "snapshot", "https://example.com")))
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        `when`(posts.findById(101)).thenReturn(Optional.empty())
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        verifyNoInteractions(caches, audit)
    }

    @Test
    fun `manual content and unavailable and blocked posts are protected`() {
        post.contentManuallyOverridden = true
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        post.contentManuallyOverridden = false
        doThrow(ParsingRecoveryException(org.every.nook.api.application.admin.ParsingRecoveryError.CONFLICT))
            .`when`(disposition).requireOpen(101)
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        `when`(content.findByPostIdForUpdate(101)).thenReturn(null)
        assertFailsWith<ParsingRecoveryException> { adapter.refetch(command) }
        verifyNoInteractions(caches, audit)
    }
}
