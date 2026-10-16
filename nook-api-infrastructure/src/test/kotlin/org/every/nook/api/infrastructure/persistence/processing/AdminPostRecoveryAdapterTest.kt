package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RetryPostParsingCommand
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.admin.AdminSavedUsersQuery
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminPostRecoveryAdapterTest {
    private val content = mock(PostContentParsingJobJpaRepository::class.java)
    private val places = mock(PlaceParsingJobJpaRepository::class.java)
    private val followUps = mock(ParsingFollowUpJobJpaRepository::class.java)
    private val audit = mock(AdminAuditLogPort::class.java)
    private val disposition = mock(PostProcessingDispositionStore::class.java)
    private val users = mock(AdminSavedUsersQuery::class.java)
    private val adapter = AdminPostRecoveryAdapter(
        content,
        places,
        followUps,
        mock(NamedParameterJdbcTemplate::class.java),
        audit,
        disposition,
        users,
    )
    private val contentJob = PostContentParsingJobEntity(101, PostContentParsingStatus.FAILED).apply {
        attemptCount = 4
        retryAttemptCount = 4
    }
    private val placeJob = PlaceParsingJobEntity(101, status = PlaceParsingStatus.FAILED).apply {
        attemptCount = 4
        retryAttemptCount = 4
    }
    private val thumbnail = ParsingFollowUpJobEntity(
        ParsingFollowUpJobType.PLACE_THUMBNAILS,
        101,
        "{}",
        nextAttemptAt = Instant.EPOCH,
        status = ParsingFollowUpJobStatus.FAILED,
    )
    private val actor = AdminActor("operator", "ops@example.com")

    init {
        setId(contentJob, 21)
        setId(placeJob, 22)
        setId(thumbnail, 23)
        `when`(content.findByPostIdForUpdate(101)).thenReturn(contentJob)
        `when`(places.findByPostIdForUpdate(101)).thenReturn(placeJob)
        `when`(followUps.findByPostIdForUpdate(101)).thenReturn(listOf(thumbnail))
        `when`(content.findAllByPostIdInOrderByPostIdDescIdAsc(listOf(101))).thenReturn(listOf(contentJob))
        `when`(places.findAllByPostIdInOrderByPostIdDescIdAsc(listOf(101))).thenReturn(listOf(placeJob))
        `when`(followUps.findAllByPostIdInOrderByPostIdDescIdAsc(listOf(101))).thenReturn(listOf(thumbnail))
        `when`(disposition.enrich(anyList())).thenAnswer { it.getArgument<List<AdminPostRecovery>>(0) }
    }

    @Test
    fun `content retry leaves place and thumbnail failures untouched and writes audit`() {
        adapter.retry(RetryPostParsingCommand(101, actor, "본문 복구", "POST_CONTENT"))
        assertEquals(PostContentParsingStatus.PENDING, contentJob.status)
        assertEquals(4, contentJob.attemptCount)
        assertEquals(0, contentJob.retryAttemptCount)
        assertEquals(PlaceParsingStatus.FAILED, placeJob.status)
        assertEquals(ParsingFollowUpJobStatus.FAILED, thumbnail.status)
        val entry = mockingDetails(audit).invocations.single().getArgument<AdminAuditLogPort.Entry>(0)
        assertEquals(actor, entry.actor)
        kotlin.test.assertContains(entry.beforeValue.orEmpty(), "POST_CONTENT")
        kotlin.test.assertFalse(entry.beforeValue.orEmpty().contains("PLACE_PARSING"))
    }

    @Test
    fun `place retry does not recollect content or retry images`() {
        contentJob.status = PostContentParsingStatus.COMPLETED
        adapter.retry(RetryPostParsingCommand(101, actor, "장소 복구", "PLACE_PARSING"))
        assertEquals(PostContentParsingStatus.COMPLETED, contentJob.status)
        assertEquals(PlaceParsingStatus.PENDING, placeJob.status)
        assertEquals(0, placeJob.retryAttemptCount)
        assertEquals(ParsingFollowUpJobStatus.FAILED, thumbnail.status)
    }

    @Test
    fun `pending completed and upstream incomplete stages cannot be retried`() {
        contentJob.status = PostContentParsingStatus.PENDING
        assertFailsWith<ParsingRecoveryException> {
            adapter.retry(RetryPostParsingCommand(101, actor, "중복", "POST_CONTENT"))
        }
        assertFailsWith<ParsingRecoveryException> {
            adapter.retry(RetryPostParsingCommand(101, actor, "선행 실패", "PLACE_PARSING"))
        }
        assertEquals(PlaceParsingStatus.FAILED, placeJob.status)
        verifyNoInteractions(audit)
    }

    @Test
    fun `omitting stage preserves retry all failed stages contract`() {
        adapter.retry(RetryPostParsingCommand(101, actor, "전체 복구"))
        assertEquals(PostContentParsingStatus.PENDING, contentJob.status)
        assertEquals(PlaceParsingStatus.PENDING, placeJob.status)
        assertEquals(ParsingFollowUpJobStatus.PENDING, thumbnail.status)
    }

    private fun setId(entity: Any, id: Long) {
        entity.javaClass.getDeclaredField("id").apply { isAccessible = true }.set(entity, id)
        org.every.nook.api.infrastructure.persistence.BaseEntity::class.java.getDeclaredField("updatedAt")
            .apply { isAccessible = true }.set(entity, Instant.EPOCH)
    }
}
