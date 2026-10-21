package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPostContentRefetchPort
import org.every.nook.api.application.admin.AdminPostRecovery
import org.every.nook.api.application.admin.AdminPostRecoveryPort
import org.every.nook.api.application.admin.ParsingRecoveryError
import org.every.nook.api.application.admin.ParsingRecoveryException
import org.every.nook.api.application.admin.RefetchPostContentCommand
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class AdminPostContentRefetchAdapter(
    private val content: PostContentParsingJobJpaRepository,
    private val places: PlaceParsingJobJpaRepository,
    private val followUps: ParsingFollowUpJobJpaRepository,
    private val posts: PostJpaRepository,
    private val caches: ScrapingProviderResponseJpaRepository,
    private val disposition: PostProcessingDispositionStore,
    private val audit: AdminAuditLogPort,
    private val recovery: AdminPostRecoveryPort,
    private val clock: Clock = Clock.systemUTC(),
) : AdminPostContentRefetchPort {
    @Transactional
    override fun refetch(command: RefetchPostContentCommand): AdminPostRecovery {
        // Lock in completion order. Scheduling is durable; the worker calls providers after commit.
        val job = content.findByPostIdForUpdate(command.postId)
            ?: throw ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
        val place = places.findByPostIdForUpdate(command.postId)
        val jobs = followUps.findByPostIdForUpdate(command.postId)
        val post = posts.findById(command.postId).orElseThrow {
            ParsingRecoveryException(ParsingRecoveryError.NOT_FOUND)
        }
        disposition.requireOpen(command.postId)
        val active = job.status in ACTIVE_CONTENT || place?.status in ACTIVE_PLACE ||
            jobs.any { it.status in ACTIVE_FOLLOW_UP }
        if (active || post.sourceType != "INSTAGRAM" ||
            post.contentManuallyOverridden
        ) {
            throw ParsingRecoveryException(ParsingRecoveryError.CONFLICT)
        }
        val before = job.status.name
        val removed = caches.deleteInstagramResponses(post.externalPostId)
        job.status = PostContentParsingStatus.PENDING
        job.retryAttemptCount = 0
        job.nextAttemptAt = clock.instant()
        job.progressStage = null
        job.progressStageStartedAt = null
        job.progressPercent = CONTENT_PENDING_PERCENT
        job.executionStage = null
        audit.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "POST_CONTENT_REFETCH_REQUESTED",
                targetType = "POST",
                targetId = command.postId.toString(),
                reason = command.reason,
                beforeValue = """{"status":"$before"}""",
                afterValue = """{"status":"PENDING","removedCaches":$removed}""",
                requestId = null,
            ),
        )
        return requireNotNull(recovery.find(command.postId))
    }

    private companion object {
        const val CONTENT_PENDING_PERCENT = 5
        val ACTIVE_CONTENT = setOf(PostContentParsingStatus.PENDING, PostContentParsingStatus.PROCESSING)
        val ACTIVE_PLACE = setOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
        val ACTIVE_FOLLOW_UP = setOf(ParsingFollowUpJobStatus.PENDING, ParsingFollowUpJobStatus.PROCESSING)
    }
}
