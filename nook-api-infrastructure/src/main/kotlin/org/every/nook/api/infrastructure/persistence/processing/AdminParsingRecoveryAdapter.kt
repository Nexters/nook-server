package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminFollowUpJob
import org.every.nook.api.application.admin.AdminFollowUpPage
import org.every.nook.api.application.admin.AdminParsingRecoveryPort
import org.every.nook.api.application.admin.ParsingRecoveryError
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RetryParsingJobCommand
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class AdminParsingRecoveryAdapter(
    private val repository: ParsingFollowUpJobJpaRepository,
    private val audit: AdminAuditLogPort,
    private val clock: Clock = Clock.systemUTC(),
) : AdminParsingRecoveryPort {
    @Transactional(readOnly = true)
    override fun find(jobId: Long): AdminFollowUpJob? = repository.findById(jobId).orElse(null)?.toAdminView()

    @Transactional(readOnly = true)
    override fun list(postId: Long?, status: String?, beforeId: Long?, limit: Int): AdminFollowUpPage {
        val rows = repository.findForAdmin(
            postId,
            status?.let(ParsingFollowUpJobStatus::valueOf),
            beforeId,
            PageRequest.of(0, limit + 1),
        )
        return AdminFollowUpPage(rows.take(limit).map { it.toAdminView() }, rows.size > limit)
    }

    @Transactional
    override fun retry(command: RetryParsingJobCommand): AdminFollowUpJob {
        val job = repository.findByIdForUpdate(command.jobId)
            ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
        if (job.status != ParsingFollowUpJobStatus.FAILED) {
            throw ParsingRecoveryException(ParsingRecoveryError.CONFLICT)
        }
        job.status = ParsingFollowUpJobStatus.PENDING
        job.retryAttemptCount = 0
        job.nextAttemptAt = clock.instant()
        audit.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "PARSING_FOLLOW_UP_RETRIED",
                targetType = "POST",
                targetId = job.postId.toString(),
                reason = command.reason,
                beforeValue = """{"jobId":${job.id},"status":"FAILED","attempt":${job.attemptCount}}""",
                afterValue = """{"jobId":${job.id},"status":"PENDING"}""",
                requestId = command.requestId,
            ),
        )
        return job.toAdminView()
    }
}

private fun ParsingFollowUpJobEntity.toAdminView() = AdminFollowUpJob(
    id = requireNotNull(id),
    postId = postId,
    type = jobType.name,
    status = status.name,
    attempts = attemptCount,
    failureReason = failureReason,
    nextAttemptAt = nextAttemptAt.takeIf { status == ParsingFollowUpJobStatus.PENDING },
    updatedAt = updatedAt,
    recoveryStatus = if (attemptCount > retryAttemptCount) {
        when (status) {
            ParsingFollowUpJobStatus.PENDING -> "RETRY_PENDING"
            ParsingFollowUpJobStatus.PROCESSING -> "RETRY_PROCESSING"
            ParsingFollowUpJobStatus.COMPLETED -> "RECOVERED"
            ParsingFollowUpJobStatus.FAILED -> "RETRY_FAILED"
        }
    } else {
        "NONE"
    },
)
