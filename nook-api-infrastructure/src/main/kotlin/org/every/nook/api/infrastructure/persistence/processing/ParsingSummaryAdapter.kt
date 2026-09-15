package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.processing.ParsingSummaryPort
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

@Component
class ParsingSummaryAdapter(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
    private val objectMapper: ObjectMapper,
) : ParsingSummaryPort {
    private val transaction = TransactionTemplate(transactionManager)

    override fun candidates(afterId: Long, limit: Int): List<Long> = jdbc.queryForList(
        """
        SELECT DISTINCT p.id FROM posts p JOIN ($JOB_ROWS) j ON j.post_id = p.id
        WHERE p.id > ? AND ((j.status = 'FAILED' AND j.last_failed_at IS NOT NULL) OR j.no_places = 1)
        ORDER BY p.id LIMIT ?
        """.trimIndent(),
        Long::class.java,
        afterId,
        limit,
    ).filterNotNull()

    override fun publish(postId: Long) {
        transaction.executeWithoutResult {
            // Serialize publishers on the parent; job reads share one committed snapshot.
            val previous = jdbc.queryForList(
                "SELECT parsing_alert_fingerprint FROM posts WHERE id = ? FOR UPDATE",
                postId,
            ).singleOrNull() ?: return@executeWithoutResult
            val jobs = jdbc.query(
                "SELECT * FROM ($JOB_ROWS) jobs WHERE post_id = ? ORDER BY type, id",
                { row, _ ->
                    ParsingSummaryJob(
                        row.getString("type"),
                        row.getLong("id"),
                        row.getString("status"),
                        row.getInt("attempt_count"),
                        row.getString("stage"),
                        row.getString("failure_reason"),
                        row.getTimestamp("last_failed_at")?.toInstant(),
                        row.getInt("retry_attempt_count"),
                        row.getBoolean("no_places"),
                    )
                },
                postId,
            )
            val summary = ParsingSummary.from(jobs) ?: return@executeWithoutResult
            if (previous["parsing_alert_fingerprint"] == summary.fingerprint) return@executeWithoutResult
            jdbc.update(
                "UPDATE posts SET parsing_alert_fingerprint = ?, updated_at = updated_at WHERE id = ?",
                summary.fingerprint,
                postId,
            )
            val failures = objectMapper.writeValueAsString(summary.failures)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    val logger = LoggerFactory.getLogger(ParsingSummaryAdapter::class.java)
                    val event = if (summary.failed > 0) logger.atError() else logger.atWarn()
                    event.addKeyValue(
                        "event_type",
                        if (summary.failed > 0) "post.parsing.summary_failed" else "post.parsing.summary_warning",
                    )
                        .addKeyValue("post_id", postId)
                        .addKeyValue("failure_summary", failures)
                        .addKeyValue("completed_count", summary.completed)
                        .addKeyValue("failed_count", summary.failed)
                        .addKeyValue("total_count", jobs.size)
                        .addKeyValue("failed_at", summary.lastFailedAt?.toString())
                        .addKeyValue("warning_code", if (summary.noPlaces) "NO_PLACES" else null)
                        .log("게시물 처리 종료: postId={}, failed={}, noPlaces={}", postId, summary.failed, summary.noPlaces)
                }
            })
        }
    }

    private companion object {
        const val JOB_ROWS = """
            SELECT post_id, 'POST_CONTENT' AS type, id, status, attempt_count, retry_attempt_count,
                COALESCE(last_failure_stage, 'POST_CONTENT') AS stage, failure_reason, last_failed_at, 0 AS no_places
            FROM post_content_parsing_jobs
            UNION ALL
            SELECT post_id, 'PLACE_PARSING' AS type, id, status, attempt_count, retry_attempt_count,
                COALESCE(last_failure_stage, 'PLACE_PARSING') AS stage, failure_reason, last_failed_at,
                (status = 'COMPLETED' AND resolved_place_count IS NOT NULL
                 AND NOT EXISTS (SELECT 1 FROM post_places pp WHERE pp.post_id = pj.post_id)) AS no_places
            FROM place_parsing_jobs pj
            UNION ALL
            SELECT post_id, job_type AS type, id, status, attempt_count, retry_attempt_count,
                job_type AS stage, failure_reason, last_failed_at, 0 AS no_places
            FROM parsing_follow_up_jobs
        """
    }
}
