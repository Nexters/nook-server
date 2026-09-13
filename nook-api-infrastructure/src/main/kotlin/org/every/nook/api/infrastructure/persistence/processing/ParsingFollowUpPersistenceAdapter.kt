package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import org.every.nook.api.application.processing.ClaimedParsingFollowUpJob
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class ParsingFollowUpPersistenceAdapter(
    private val repository: ParsingFollowUpJobJpaRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
    private val failureAlerts: ParsingFailureAlerts = ParsingFailureAlerts(),
) : ParsingFollowUpJobPort {
    @Transactional
    override fun enqueue(event: PostMediaStorageRequestedEvent) {
        save(ParsingFollowUpJobType.POST_MEDIA, event.postId, event, event.availableAt)
    }

    @Transactional
    override fun enqueue(event: PlaceThumbnailsRequestedEvent) {
        save(ParsingFollowUpJobType.PLACE_THUMBNAILS, event.postId, event, event.availableAt)
    }

    @Transactional
    override fun enqueue(event: PlaceTagsRequestedEvent) {
        save(ParsingFollowUpJobType.PLACE_TAGS, event.postId, event, null)
    }

    @Transactional
    override fun claim(limit: Int, processingTimeout: Duration): List<ClaimedParsingFollowUpJob> {
        val now = clock.instant()
        val ids = repository.findClaimableIds(now, now.minus(processingTimeout), PageRequest.of(0, limit))
        return ids.mapNotNull { id ->
            val job = repository.findByIdForUpdate(id) ?: return@mapNotNull null
            if (!job.isAvailable(now, processingTimeout)) return@mapNotNull null
            job.status = ParsingFollowUpJobStatus.PROCESSING
            job.attemptCount += 1
            job.retryAttemptCount += 1
            job.nextAttemptAt = now
            job.toClaimed()
        }
    }

    @Transactional
    override fun complete(jobId: Long, attempt: Int): Boolean {
        val job = repository.findByIdForUpdate(jobId) ?: return false
        if (!job.isCurrentAttempt(attempt)) return false
        job.status = ParsingFollowUpJobStatus.COMPLETED
        job.failureReason = null
        return true
    }

    @Transactional
    override fun retry(jobId: Long, attempt: Int, availableAt: Instant, reason: String): Boolean {
        val job = repository.findByIdForUpdate(jobId) ?: return false
        if (!job.isCurrentAttempt(attempt)) return false
        job.status = ParsingFollowUpJobStatus.PENDING
        job.nextAttemptAt = availableAt
        job.lastFailedAt = clock.instant()
        job.failureReason = reason.take(ParsingFollowUpJobEntity.FAILURE_REASON_LENGTH)
        return true
    }

    @Transactional
    override fun fail(jobId: Long, attempt: Int, reason: String): Boolean {
        val job = repository.findByIdForUpdate(jobId) ?: return false
        if (!job.isCurrentAttempt(attempt)) return false
        job.status = ParsingFollowUpJobStatus.FAILED
        job.lastFailedAt = clock.instant()
        job.failureReason = reason.take(ParsingFollowUpJobEntity.FAILURE_REASON_LENGTH)
        failureAlerts.afterCommit(
            job.postId,
            job.jobType.name,
            jobId,
            attempt,
            job.jobType.name,
            requireNotNull(job.lastFailedAt),
        )
        return true
    }

    @Transactional
    override fun writeResult(jobId: Long, attempt: Int, change: () -> Unit): Boolean {
        val job = repository.findByIdForUpdate(jobId) ?: return false
        if (!job.isCurrentAttempt(attempt)) return false
        change()
        return true
    }

    private fun save(type: ParsingFollowUpJobType, postId: Long, payload: Any, availableAt: Instant?) {
        repository.save(
            ParsingFollowUpJobEntity(
                jobType = type,
                postId = postId,
                payload = objectMapper.writeValueAsString(payload),
                nextAttemptAt = availableAt ?: clock.instant(),
            ),
        )
    }

    private fun ParsingFollowUpJobEntity.toClaimed(): ClaimedParsingFollowUpJob = when (jobType) {
        ParsingFollowUpJobType.POST_MEDIA -> ClaimedParsingFollowUpJob.Media(
            requireNotNull(id),
            attemptCount,
            objectMapper.readValue<PostMediaStorageRequestedEvent>(payload),
            retryAttemptCount,
        )

        ParsingFollowUpJobType.PLACE_THUMBNAILS -> ClaimedParsingFollowUpJob.Thumbnails(
            requireNotNull(id),
            attemptCount,
            objectMapper.readValue<PlaceThumbnailsRequestedEvent>(payload),
            retryAttemptCount,
        )

        ParsingFollowUpJobType.PLACE_TAGS -> ClaimedParsingFollowUpJob.Tags(
            requireNotNull(id),
            attemptCount,
            objectMapper.readValue<PlaceTagsRequestedEvent>(payload),
            retryAttemptCount,
        )
    }

    private fun ParsingFollowUpJobEntity.isAvailable(now: Instant, timeout: Duration): Boolean = when (status) {
        ParsingFollowUpJobStatus.PENDING -> !nextAttemptAt.isAfter(now)

        ParsingFollowUpJobStatus.PROCESSING -> !updatedAt.plus(timeout).isAfter(now)

        ParsingFollowUpJobStatus.COMPLETED,
        ParsingFollowUpJobStatus.FAILED,
        -> false
    }
}

private fun ParsingFollowUpJobEntity.isCurrentAttempt(attempt: Int): Boolean =
    status == ParsingFollowUpJobStatus.PROCESSING && attemptCount == attempt
