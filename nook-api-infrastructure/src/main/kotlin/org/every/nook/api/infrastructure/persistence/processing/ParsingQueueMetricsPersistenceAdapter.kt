package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.processing.ParsingQueueMetricsPort
import org.every.nook.api.application.processing.ParsingQueueObservation
import org.every.nook.api.application.processing.ProcessingTimeouts
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ParsingQueueMetricsPersistenceAdapter(
    private val contentJobs: PostContentParsingJobJpaRepository,
    private val placeJobs: PlaceParsingJobJpaRepository,
    private val followUpJobs: ParsingFollowUpJobJpaRepository,
) : ParsingQueueMetricsPort {
    @Transactional(readOnly = true)
    override fun observe(now: Instant, processingTimeouts: ProcessingTimeouts): List<ParsingQueueObservation> = listOf(
        ParsingQueueObservation(
            queue = "content",
            readyJobs = contentJobs.countByStatusAndNextAttemptAtLessThanEqual(
                PostContentParsingStatus.PENDING,
                now,
            ),
            oldestReadyAt = contentJobs.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                PostContentParsingStatus.PENDING,
                now,
            )?.nextAttemptAt,
            processingJobs = contentJobs.countByStatus(PostContentParsingStatus.PROCESSING),
            stuckProcessingJobs = contentJobs.countByStatusAndUpdatedAtLessThanEqual(
                PostContentParsingStatus.PROCESSING,
                now.minus(processingTimeouts.content),
            ),
            failedJobs = contentJobs.countByStatus(PostContentParsingStatus.FAILED),
        ),
        ParsingQueueObservation(
            queue = "place",
            readyJobs = placeJobs.countByStatusAndNextAttemptAtLessThanEqual(PlaceParsingStatus.PENDING, now),
            oldestReadyAt = placeJobs.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                PlaceParsingStatus.PENDING,
                now,
            )?.nextAttemptAt,
            processingJobs = placeJobs.countByStatus(PlaceParsingStatus.PROCESSING),
            stuckProcessingJobs = placeJobs.countByStatusAndUpdatedAtLessThanEqual(
                PlaceParsingStatus.PROCESSING,
                now.minus(processingTimeouts.place),
            ),
            failedJobs = placeJobs.countByStatus(PlaceParsingStatus.FAILED),
        ),
        ParsingQueueObservation(
            queue = "follow_up",
            readyJobs = followUpJobs.countByStatusAndNextAttemptAtLessThanEqual(
                ParsingFollowUpJobStatus.PENDING,
                now,
            ),
            oldestReadyAt = followUpJobs.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                ParsingFollowUpJobStatus.PENDING,
                now,
            )?.nextAttemptAt,
            processingJobs = followUpJobs.countByStatus(ParsingFollowUpJobStatus.PROCESSING),
            stuckProcessingJobs = followUpJobs.countByStatusAndUpdatedAtLessThanEqual(
                ParsingFollowUpJobStatus.PROCESSING,
                now.minus(processingTimeouts.followUp),
            ),
            failedJobs = followUpJobs.countByStatus(ParsingFollowUpJobStatus.FAILED),
        ),
    )
}
