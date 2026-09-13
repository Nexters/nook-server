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
    private val failureClassifier: ParsingFailureClassifier = ParsingFailureClassifier.DEFAULT,
) {
    init {
        require(!processingTimeout.isNegative && !processingTimeout.isZero) { "Processing timeout must be positive" }
        require(!retryBackoff.isNegative && !retryBackoff.isZero) { "Retry backoff must be positive" }
        require(batchSize > 0) { "Follow-up job batch size must be positive" }
        require(maxAttempts > 0) { "Follow-up job max attempts must be positive" }
    }

    operator fun invoke(): Int {
        var processed = 0
        repeat(batchSize) {
            val job = jobPort.claim(1, processingTimeout).singleOrNull() ?: return processed
            process(job)
            processed += 1
        }
        return processed
    }

    private fun process(job: ClaimedParsingFollowUpJob) {
        val writer = ParsingResultWriter { change ->
            if (!jobPort.writeResult(job.id, job.attempt, change)) throw StaleParsingExecutionException()
        }
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
                    writer,
                )

                is ClaimedParsingFollowUpJob.Thumbnails ->
                    storePlaceThumbnail(job.event.postId, job.event.requests, writer)

                is ClaimedParsingFollowUpJob.Tags -> storePlaceTags(job.event, writer)
            }
        }.onSuccess {
            jobPort.complete(job.id, job.attempt)
        }.onFailure { exception ->
            if (exception is StaleParsingExecutionException) return@onFailure
            val failure = failureClassifier.classify(exception)
            val reason = failure.storedReason()
            if (failure.kind.retryable && job.retryAttempt < maxAttempts) {
                val delay = maxOf(
                    retryBackoff.multipliedBy(job.retryAttempt.toLong()),
                    failure.retryAfter ?: Duration.ZERO,
                )
                jobPort.retry(job.id, job.attempt, clock.instant().plus(delay), reason)
            } else {
                jobPort.fail(job.id, job.attempt, reason)
            }
        }
    }
}
