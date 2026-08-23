package org.every.nook.api.application.processing

import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.post.StorePostMediaUseCase
import java.time.Clock
import java.time.Duration

class ProcessParsingFollowUpJobsUseCase(
    private val jobPort: ParsingFollowUpJobPort,
    private val storePostMedia: StorePostMediaUseCase,
    private val storePlaceThumbnail: StorePlaceThumbnailUseCase,
    private val storePlaceTags: StorePlaceTagsUseCase,
    private val batchSize: Int,
    private val processingTimeout: Duration,
    private val retryBackoff: Duration,
    private val maxAttempts: Int = 4,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(batchSize > 0) { "Follow-up job batch size must be positive" }
        require(maxAttempts > 0) { "Follow-up job max attempts must be positive" }
    }

    operator fun invoke(): Int {
        val jobs = jobPort.claim(batchSize, processingTimeout)
        jobs.forEach(::process)
        return jobs.size
    }

    private fun process(job: ClaimedParsingFollowUpJob) {
        runCatching {
            when (job) {
                is ClaimedParsingFollowUpJob.Media -> storePostMedia(
                    job.event.postId,
                    StorePostMediaUseCase.Command(
                        job.event.mediaType,
                        job.event.sourceUrl,
                        job.event.sequence,
                        job.event.sourceThumbnailUrl,
                    ),
                )

                is ClaimedParsingFollowUpJob.Thumbnails ->
                    storePlaceThumbnail(job.event.postId, job.event.requests)

                is ClaimedParsingFollowUpJob.Tags -> storePlaceTags(job.event)
            }
        }.onSuccess {
            jobPort.complete(job.id)
        }.onFailure { exception ->
            val reason = exception.message.orEmpty().ifBlank { "Parsing follow-up job failed" }
            if (job.attempt < maxAttempts) {
                jobPort.retry(job.id, clock.instant().plus(retryBackoff), reason)
            } else {
                jobPort.fail(job.id, reason)
            }
        }
    }
}
