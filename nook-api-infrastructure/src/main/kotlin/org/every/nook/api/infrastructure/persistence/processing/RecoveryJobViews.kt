package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminFollowUpJob
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity

internal fun PostContentParsingJobEntity.toRecoveryView() = AdminFollowUpJob(
    id = requireNotNull(id), postId = postId, type = "POST_CONTENT", status = status.name,
    attempts = attemptCount, failureReason = failureReason,
    nextAttemptAt = nextAttemptAt.takeIf { status.name == "PENDING" }, updatedAt = updatedAt,
    recoveryStatus = manualRecoveryStatus(status.name, attemptCount, retryAttemptCount),
    lastFailedAt = lastFailedAt, failureStage = lastFailureStage,
)

internal fun PlaceParsingJobEntity.toRecoveryView() = AdminFollowUpJob(
    id = requireNotNull(id), postId = postId, type = "PLACE_PARSING", status = status.name,
    attempts = attemptCount, failureReason = failureReason,
    nextAttemptAt = nextAttemptAt.takeIf { status.name == "PENDING" }, updatedAt = updatedAt,
    recoveryStatus = manualRecoveryStatus(status.name, attemptCount, retryAttemptCount),
    lastFailedAt = lastFailedAt, failureStage = lastFailureStage,
)

private fun manualRecoveryStatus(status: String, total: Int, current: Int): String = when {
    total <= current -> "NONE"
    status == "COMPLETED" -> "RECOVERED"
    else -> "RETRY_$status"
}
