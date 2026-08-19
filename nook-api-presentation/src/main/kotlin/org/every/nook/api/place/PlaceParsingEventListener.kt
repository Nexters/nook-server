package org.every.nook.api.place

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent
import org.every.nook.api.application.place.ProcessPlaceParsingJobUseCase
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.withProcessingLogContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class PlaceParsingEventListener(
    private val processPlaceParsingJob: ProcessPlaceParsingJobUseCase,
    private val findOutstandingJobs: FindOutstandingPlaceParsingJobsUseCase,
    private val storePlaceThumbnail: StorePlaceThumbnailUseCase,
    private val storePlaceTags: StorePlaceTagsUseCase,
    private val eventPublisher: ApplicationEventPublisher,
    @Qualifier("parsingRetryTaskScheduler") private val retryTaskScheduler: TaskScheduler,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    @EventListener(ApplicationReadyEvent::class)
    @SchedulerLock(
        name = "placeParsing.recoverOutstandingJobs",
        lockAtMostFor = "1m",
        lockAtLeastFor = "10s",
    )
    fun recoverOutstandingJobs() {
        val jobs = findOutstandingJobs()
        logger.info { "Recovering outstanding place parsing jobs: jobCount=${jobs.size}" }
        jobs.forEach { job ->
            eventPublisher.publishEvent(
                PlaceParsingJobRequestedEvent(
                    postId = job.postId,
                    availableAt = job.availableAt,
                ),
            )
        }
    }

    @Scheduled(fixedDelayString = "\${parsing.dispatcher-interval:10s}")
    @SchedulerLock(
        name = "placeParsing.dispatchOutstandingJobs",
        lockAtMostFor = "30s",
        lockAtLeastFor = "9s",
    )
    fun dispatchOutstandingJobs() {
        val now = clock.instant()
        val jobs = findOutstandingJobs().filterNot { it.availableAt.isAfter(now) }
        if (jobs.isNotEmpty()) {
            logger.info { "Dispatching outstanding place parsing jobs: jobCount=${jobs.size}" }
        }
        jobs.forEach { job ->
            eventPublisher.publishEvent(
                PlaceParsingJobRequestedEvent(
                    postId = job.postId,
                    availableAt = job.availableAt,
                ),
            )
        }
    }

    @Async("placeParsingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun process(event: PlaceParsingJobRequestedEvent) {
        withProcessingLogContext(event.postId, PLACE_FLOW) {
            logger.info {
                "Place parsing event received: postId=${event.postId}, availableAt=${event.availableAt}"
            }
            if (scheduleWhenUnavailable(event)) {
                return@withProcessingLogContext
            }
            recordQueueDelay(event.availableAt, event.postId)
            when (val result = processPlaceParsingJob(event.postId)) {
                is ProcessPlaceParsingJobUseCase.Result.Retry -> schedule(
                    PlaceParsingJobRequestedEvent(event.postId, result.nextAttemptAt),
                )

                ProcessPlaceParsingJobUseCase.Result.Completed,
                ProcessPlaceParsingJobUseCase.Result.Failed,
                ProcessPlaceParsingJobUseCase.Result.Skipped,
                -> Unit
            }
        }
    }

    @Async("placeSupplementTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun storeThumbnail(event: PlaceThumbnailsRequestedEvent) {
        withProcessingLogContext(event.postId, THUMBNAIL_FLOW) {
            runCatching {
                storePlaceThumbnail(event.postId, event.requests)
            }.onFailure { exception ->
                logger.warn(exception) {
                    "Place thumbnail storage failed: postId=${event.postId}, placeCount=${event.requests.size}"
                }
            }
        }
    }

    @Async("placeParsingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun storeTags(event: PlaceTagsRequestedEvent) {
        withProcessingLogContext(event.postId, TAG_FLOW) {
            runCatching {
                storePlaceTags(event)
            }.onFailure { exception ->
                logger.warn(exception) {
                    "Place tag storage failed: postId=${event.postId}, placeId=${event.placeId}, " +
                        "provider=${event.place.provider}, externalPlaceId=${event.place.externalPlaceId}"
                }
            }
        }
    }

    private fun scheduleWhenUnavailable(event: PlaceParsingJobRequestedEvent): Boolean {
        val availableAt = event.availableAt ?: return false
        if (!availableAt.isAfter(clock.instant())) {
            return false
        }
        schedule(event)
        return true
    }

    private fun schedule(event: PlaceParsingJobRequestedEvent) {
        retryTaskScheduler.schedule(
            { eventPublisher.publishEvent(event) },
            requireNotNull(event.availableAt),
        )
    }

    private fun recordQueueDelay(availableAt: Instant?, postId: Long) {
        val target = availableAt ?: return
        val delay = Duration.between(target, clock.instant()).coerceAtLeast(Duration.ZERO)
        metrics.record(
            ProcessingMetrics.Measurement(
                flow = PLACE_FLOW,
                stage = QUEUE_STAGE,
                postId = postId,
                attempt = null,
                outcome = ProcessingMetrics.Outcome.SUCCESS,
                duration = delay,
            ),
        )
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val PLACE_FLOW = "place"
        const val THUMBNAIL_FLOW = "place-thumbnail"
        const val TAG_FLOW = "place-tags"
        const val QUEUE_STAGE = "queue"
    }
}
