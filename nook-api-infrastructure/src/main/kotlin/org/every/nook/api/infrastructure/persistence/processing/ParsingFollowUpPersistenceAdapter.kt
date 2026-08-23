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
            job.nextAttemptAt = now
            job.toClaimed()
        }
    }

    @Transactional
    override fun complete(jobId: Long) {
        val job = requireNotNull(repository.findByIdForUpdate(jobId))
        check(job.status == ParsingFollowUpJobStatus.PROCESSING)
        job.status = ParsingFollowUpJobStatus.COMPLETED
        job.failureReason = null
    }

    @Transactional
    override fun retry(jobId: Long, availableAt: Instant, reason: String) {
        val job = requireNotNull(repository.findByIdForUpdate(jobId))
        check(job.status == ParsingFollowUpJobStatus.PROCESSING)
        job.status = ParsingFollowUpJobStatus.PENDING
        job.nextAttemptAt = availableAt
        job.failureReason = reason.take(ParsingFollowUpJobEntity.FAILURE_REASON_LENGTH)
    }

    @Transactional
    override fun fail(jobId: Long, reason: String) {
        val job = requireNotNull(repository.findByIdForUpdate(jobId))
        check(job.status == ParsingFollowUpJobStatus.PROCESSING)
        job.status = ParsingFollowUpJobStatus.FAILED
        job.failureReason = reason.take(ParsingFollowUpJobEntity.FAILURE_REASON_LENGTH)
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
        )

        ParsingFollowUpJobType.PLACE_THUMBNAILS -> ClaimedParsingFollowUpJob.Thumbnails(
            requireNotNull(id),
            attemptCount,
            objectMapper.readValue<PlaceThumbnailsRequestedEvent>(payload),
        )

        ParsingFollowUpJobType.PLACE_TAGS -> ClaimedParsingFollowUpJob.Tags(
            requireNotNull(id),
            attemptCount,
            objectMapper.readValue<PlaceTagsRequestedEvent>(payload),
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
