package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPostProcessingPort
import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.AdminPostRecoveryPort
import org.every.nook.api.application.admin.ChangePostDisposition
import org.every.nook.api.application.admin.ParsingRecoveryError
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.PostProcessingPage
import org.every.nook.api.application.admin.PostProcessingQuery
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Component
class AdminPostProcessingAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
    private val recovery: AdminPostRecoveryPort,
    private val disposition: PostProcessingDispositionStore,
    private val content: PostContentParsingJobJpaRepository,
    private val place: PlaceParsingJobJpaRepository,
    private val followUps: ParsingFollowUpJobJpaRepository,
    private val audit: AdminAuditLogPort,
) : AdminPostProcessingPort {
    @Transactional(readOnly = true)
    override fun list(query: PostProcessingQuery): PostProcessingPage {
        val params = mapOf(
            "category" to query.category,
            "since" to query.failedSince?.let(Timestamp::from),
            "postId" to query.postId,
            "offset" to (query.page - 1) * query.limit,
            "limit" to query.limit,
        )
        val ids = jdbc.queryForList(
            "SELECT p.id $FILTER ORDER BY j.failed_at DESC, p.id DESC LIMIT :limit OFFSET :offset",
            params,
            Long::class.java,
        ).filterNotNull()
        val total = jdbc.queryForObject("SELECT COUNT(*) $FILTER", params, Long::class.java) ?: 0L
        return PostProcessingPage(ids.mapNotNull(recovery::find), total)
    }

    @Transactional
    override fun change(command: ChangePostDisposition): AdminPostRecovery {
        content.findByPostIdForUpdate(command.postId)
        place.findByPostIdForUpdate(command.postId)
        followUps.findByPostIdForUpdate(command.postId)
        val previous = disposition.lock(command.postId)
        val post = recovery.find(command.postId) ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
        val noFailureToClassify = command.disposition == "UNPROCESSABLE" && post.jobs.none { it.status == "FAILED" }
        if (previous == command.disposition || post.jobs.any { it.status in setOf("PENDING", "PROCESSING") } ||
            noFailureToClassify
        ) {
            throw ParsingRecoveryException(ParsingRecoveryError.DISPOSITION_CONFLICT)
        }
        jdbc.update(
            """
            UPDATE posts SET processing_disposition = :state, processing_disposition_reason = :reason,
                processing_disposition_changed_at = CURRENT_TIMESTAMP(6), updated_at = updated_at WHERE id = :id
            """.trimIndent(),
            mapOf("state" to command.disposition, "reason" to command.reason, "id" to command.postId),
        )
        audit.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "POST_PROCESSING_DISPOSITION_CHANGED",
                targetType = "POST",
                targetId = command.postId.toString(),
                reason = command.reason,
                beforeValue = "{\"disposition\":\"$previous\"}",
                afterValue = "{\"disposition\":\"${command.disposition}\"}",
                requestId = null,
            ),
        )
        return requireNotNull(recovery.find(command.postId))
    }

    private companion object {
        const val FILTER = """
            FROM posts p JOIN (
                SELECT post_id, MAX(CASE WHEN status='FAILED' THEN last_failed_at END) AS failed_at,
                    SUM(status='FAILED') AS failures, SUM(status IN ('PENDING','PROCESSING')) AS active,
                    SUM(status='FAILED' AND attempt_count > retry_attempt_count) AS retry_failures
                FROM (
                    SELECT post_id,status,last_failed_at,attempt_count,retry_attempt_count FROM post_content_parsing_jobs
                    UNION ALL SELECT post_id,status,last_failed_at,attempt_count,retry_attempt_count FROM place_parsing_jobs
                    UNION ALL SELECT post_id,status,last_failed_at,attempt_count,retry_attempt_count FROM parsing_follow_up_jobs
                ) jobs GROUP BY post_id
            ) j ON j.post_id=p.id
            WHERE (:postId IS NULL OR p.id=:postId) AND (:since IS NULL OR j.failed_at >= :since)
              AND (
                :category='ALL' OR (:category='UNPROCESSABLE' AND p.processing_disposition='UNPROCESSABLE') OR
                (p.processing_disposition='OPEN' AND (
                    (:category='INITIAL_FAILED' AND j.failures>0 AND j.retry_failures=0 AND j.active=0) OR
                    (:category='RETRY_FAILED' AND j.retry_failures>0 AND j.active=0) OR
                    (:category='ACTIVE' AND j.active>0) OR
                    (:category='COMPLETED' AND j.failures=0 AND j.active=0)
                ))
              )
        """
    }
}
