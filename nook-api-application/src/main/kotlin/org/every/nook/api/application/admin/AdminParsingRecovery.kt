package org.every.nook.api.application.admin

import org.every.nook.api.application.error.ErrorType
import org.every.nook.api.application.error.NookErrorCode
import org.every.nook.api.application.error.NookException
import org.every.nook.api.application.processing.ProcessingFailureDetail
import org.every.nook.api.application.processing.processingFailureDetail
import java.time.Instant

data class AdminFollowUpJob(
    val id: Long,
    val postId: Long,
    val type: String,
    val status: String,
    val attempts: Int,
    val failureReason: String?,
    val nextAttemptAt: Instant?,
    val updatedAt: Instant,
    val recoveryStatus: String = "NONE",
    val lastFailedAt: Instant? = null,
    val failureStage: String? = null,
    val failure: ProcessingFailureDetail? = failureReason?.let(::processingFailureDetail),
    val outcome: String? = null,
)

data class AdminFollowUpPage(val jobs: List<AdminFollowUpJob>, val hasNext: Boolean)

data class RetryParsingJobCommand(val jobId: Long, val actor: AdminActor, val reason: String, val requestId: String?)

interface AdminParsingRecoveryPort {
    fun find(jobId: Long): AdminFollowUpJob?
    fun list(postId: Long?, status: String?, beforeId: Long?, limit: Int): AdminFollowUpPage
    fun retry(command: RetryParsingJobCommand): AdminFollowUpJob
}

class GetAdminFollowUpJobUseCase(private val port: AdminParsingRecoveryPort) {
    operator fun invoke(jobId: Long): AdminFollowUpJob {
        require(jobId > 0)
        return port.find(jobId) ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
    }
}

class ListAdminFollowUpJobsUseCase(private val port: AdminParsingRecoveryPort) {
    operator fun invoke(postId: Long?, status: String?, beforeId: Long?, limit: Int): AdminFollowUpPage {
        require(postId == null || postId > 0)
        require(beforeId == null || beforeId > 0)
        require(limit in 1..MAX_PAGE_SIZE)
        require(status == null || status in setOf("PENDING", "PROCESSING", "COMPLETED", "FAILED"))
        return port.list(postId, status, beforeId, limit)
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

class RetryAdminFollowUpJobUseCase(private val port: AdminParsingRecoveryPort) {
    operator fun invoke(command: RetryParsingJobCommand): AdminFollowUpJob {
        require(command.jobId > 0)
        require(command.reason.isNotBlank() && command.reason.length <= MAX_REASON_LENGTH)
        return port.retry(command.copy(reason = command.reason.trim()))
    }

    private companion object {
        const val MAX_REASON_LENGTH = 500
    }
}

class ParsingRecoveryException(code: ParsingRecoveryError) : NookException(code)

enum class ParsingRecoveryError(
    override val code: String,
    override val defaultReason: String,
    override val type: ErrorType,
) : NookErrorCode {
    NOT_FOUND("PARSING_JOB_NOT_FOUND", "파싱 작업을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    DISPOSITION_CONFLICT("POST_PROCESSING_STATE_CONFLICT", "현재 처리 상태에서는 변경할 수 없습니다. 새로고침해 주세요.", ErrorType.CONFLICT),
    CONFLICT("PARSING_JOB_CONFLICT", "실패 상태의 작업만 재시도할 수 있습니다. 새로고침해 주세요.", ErrorType.CONFLICT),
}
