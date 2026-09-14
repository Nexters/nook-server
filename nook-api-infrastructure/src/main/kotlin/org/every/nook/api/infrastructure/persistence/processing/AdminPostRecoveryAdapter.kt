package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminFollowUpJob
import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.AdminPostRecoveryPage
import org.every.nook.api.application.admin.AdminPostRecoveryPort
import org.every.nook.api.application.admin.ParsingRecoveryError
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RetryPostParsingCommand
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class AdminPostRecoveryAdapter(
    private val content: PostContentParsingJobJpaRepository,
    private val place: PlaceParsingJobJpaRepository,
    private val followUps: ParsingFollowUpJobJpaRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    private val audit: AdminAuditLogPort,
    private val disposition: PostProcessingDispositionStore,
    private val clock: Clock = Clock.systemUTC(),
) : AdminPostRecoveryPort {
    @Transactional(readOnly = true)
    override fun list(postId: Long?, status: String?, beforePostId: Long?, limit: Int): AdminPostRecoveryPage {
        val ids = jdbc.queryForList(
            """
            SELECT DISTINCT post_id FROM (
              SELECT post_id, status FROM post_content_parsing_jobs
              UNION ALL SELECT post_id, status FROM place_parsing_jobs
              UNION ALL SELECT post_id, status FROM parsing_follow_up_jobs
            ) jobs WHERE (:postId IS NULL OR post_id = :postId)
                AND (:status IS NULL OR status = :status)
                AND (:beforeId IS NULL OR post_id < :beforeId)
            ORDER BY post_id DESC LIMIT :limit
            """.trimIndent(),
            mapOf("postId" to postId, "status" to status, "beforeId" to beforePostId, "limit" to limit + 1),
            Long::class.java,
        )
        return AdminPostRecoveryPage(load(ids.take(limit).filterNotNull()), ids.size > limit)
    }

    @Transactional(readOnly = true)
    override fun find(postId: Long): AdminPostRecovery? = load(listOf(postId)).singleOrNull()

    @Transactional
    override fun retry(command: RetryPostParsingCommand): AdminPostRecovery {
        // Stable lock order matches content completion (content -> place -> follow-up).
        val contentJob = content.findByPostIdForUpdate(command.postId)
        val placeJob = place.findByPostIdForUpdate(command.postId)
        val jobs = followUps.findByPostIdForUpdate(command.postId)
        disposition.requireOpen(command.postId)
        val before = load(listOf(command.postId)).singleOrNull()
            ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
        if (before.jobs.none { it.status == "FAILED" }) throw ParsingRecoveryException(ParsingRecoveryError.CONFLICT)
        contentJob?.takeIf { it.status == PostContentParsingStatus.FAILED }?.apply {
            status = PostContentParsingStatus.PENDING
            retryAttemptCount = 0
            nextAttemptAt = clock.instant()
        }
        placeJob?.takeIf { it.status == PlaceParsingStatus.FAILED }?.apply {
            status = PlaceParsingStatus.PENDING
            retryAttemptCount = 0
            nextAttemptAt = clock.instant()
        }
        jobs.filter { it.status == ParsingFollowUpJobStatus.FAILED }.forEach {
            it.status = ParsingFollowUpJobStatus.PENDING
            it.retryAttemptCount = 0
            it.nextAttemptAt = clock.instant()
        }
        audit.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "POST_FAILED_PARSING_RETRIED",
                targetType = "POST",
                targetId = command.postId.toString(),
                reason = command.reason,
                beforeValue = before.jobs.filter { it.status == "FAILED" }
                    .joinToString(
                        prefix = "[",
                        postfix = "]",
                    ) { """{"type":"${it.type}","jobId":${it.id},"attempt":${it.attempts}}""" },
                afterValue = """{"status":"PENDING"}""",
                requestId = null,
            ),
        )
        return requireNotNull(find(command.postId))
    }

    private fun load(ids: List<Long>): List<AdminPostRecovery> {
        if (ids.isEmpty()) return emptyList()
        val jobs = content.findAllByPostIdInOrderByPostIdDescIdAsc(ids).map { it.toRecoveryView() } +
            place.findAllByPostIdInOrderByPostIdDescIdAsc(ids).map { it.toRecoveryView() } +
            followUps.findAllByPostIdInOrderByPostIdDescIdAsc(ids).map { it.toAdminView() }
        val grouped = jobs.groupBy(AdminFollowUpJob::postId)
        return disposition.enrich(ids.mapNotNull { id -> grouped[id]?.let { AdminPostRecovery(id, it) } })
    }
}
