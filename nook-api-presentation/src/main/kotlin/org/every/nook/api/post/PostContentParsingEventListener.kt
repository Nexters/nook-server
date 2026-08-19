package org.every.nook.api.post

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.every.nook.api.application.post.PostContentParsingJobRequestedEvent
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import org.every.nook.api.application.post.ProcessPostContentParsingJobUseCase
import org.every.nook.api.application.post.StorePostMediaUseCase
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.withProcessingLogContext
import org.every.nook.api.application.push.SendPostProcessingPushUseCase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
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
class PostContentParsingEventListener(
    private val processPostContentParsingJob: ProcessPostContentParsingJobUseCase,
    private val findOutstandingJobs: FindOutstandingPostContentParsingJobsUseCase,
    private val storePostMedia: StorePostMediaUseCase,
    private val sendPostProcessingPush: SendPostProcessingPushUseCase,
    private val eventPublisher: ApplicationEventPublisher,
    @Qualifier("parsingRetryTaskScheduler") private val retryTaskScheduler: TaskScheduler,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    @Value("\${post-content-parsing.worker.media-retry-backoff:3s}")
    private val mediaRetryBackoff: Duration = Duration.ofSeconds(DEFAULT_MEDIA_RETRY_BACKOFF_SECONDS),
    private val clock: Clock = Clock.systemUTC(),
) {
    @EventListener(ApplicationReadyEvent::class)
    @SchedulerLock(
        name = "postContentParsing.recoverOutstandingJobs",
        lockAtMostFor = "1m",
        lockAtLeastFor = "10s",
    )
    fun recoverOutstandingJobs() {
        val jobs = findOutstandingJobs()
        logger.info { "Recovering outstanding post content parsing jobs: jobCount=${jobs.size}" }
        jobs.forEach { job ->
            eventPublisher.publishEvent(
                PostContentParsingJobRequestedEvent(
                    postId = job.postId,
                    availableAt = job.availableAt,
                ),
            )
        }
    }

    @Scheduled(fixedDelayString = "\${parsing.dispatcher-interval:10s}")
    @SchedulerLock(
        name = "postContentParsing.dispatchOutstandingJobs",
        lockAtMostFor = "30s",
        lockAtLeastFor = "9s",
    )
    fun dispatchOutstandingJobs() {
        val now = clock.instant()
        val jobs = findOutstandingJobs().filterNot { it.availableAt.isAfter(now) }
        if (jobs.isNotEmpty()) {
            logger.info { "Dispatching outstanding post content parsing jobs: jobCount=${jobs.size}" }
        }
        jobs.forEach { job ->
            eventPublisher.publishEvent(
                PostContentParsingJobRequestedEvent(
                    postId = job.postId,
                    availableAt = job.availableAt,
                ),
            )
        }
    }

    @Async("postContentParsingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun process(event: PostContentParsingJobRequestedEvent) {
        withProcessingLogContext(event.postId, CONTENT_FLOW) {
            logger.info {
                "Post content parsing event received: postId=${event.postId}, availableAt=${event.availableAt}"
            }
            if (scheduleWhenUnavailable(event)) {
                return@withProcessingLogContext
            }
            recordQueueDelay(event.availableAt, event.postId)
            when (val result = processPostContentParsingJob(event.postId)) {
                is ProcessPostContentParsingJobUseCase.Result.Retry -> schedule(
                    PostContentParsingJobRequestedEvent(event.postId, result.nextAttemptAt),
                )

                ProcessPostContentParsingJobUseCase.Result.Completed -> Unit

                ProcessPostContentParsingJobUseCase.Result.Failed -> sendPush(event.postId)

                ProcessPostContentParsingJobUseCase.Result.Skipped,
                -> Unit
            }
        }
    }

    @Async("postContentParsingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun storeMedia(event: PostMediaStorageRequestedEvent) {
        withProcessingLogContext(event.postId, MEDIA_FLOW) {
            runCatching {
                storePostMedia(
                    event.postId,
                    StorePostMediaUseCase.Command(event.mediaType, event.sourceUrl, event.sequence),
                )
            }.onFailure { exception ->
                if (event.attempt < MEDIA_MAX_ATTEMPTS) {
                    val retryAt = clock.instant().plus(mediaRetryBackoff)
                    scheduleMedia(event.copy(attempt = event.attempt + 1, availableAt = retryAt))
                    logger.warn(exception) {
                        "Post media storage retry scheduled: postId=${event.postId}, sequence=${event.sequence}, " +
                            "attempt=${event.attempt}, nextAttemptAt=$retryAt"
                    }
                } else {
                    logger.error(exception) {
                        "Post media storage failed permanently: postId=${event.postId}, " +
                            "sequence=${event.sequence}, attempt=${event.attempt}"
                    }
                }
            }
        }
    }

    private fun scheduleWhenUnavailable(event: PostContentParsingJobRequestedEvent): Boolean {
        val availableAt = event.availableAt ?: return false
        if (!availableAt.isAfter(clock.instant())) {
            return false
        }
        schedule(event)
        return true
    }

    private fun schedule(event: PostContentParsingJobRequestedEvent) {
        retryTaskScheduler.schedule(
            { eventPublisher.publishEvent(event) },
            requireNotNull(event.availableAt),
        )
    }

    private fun scheduleMedia(event: PostMediaStorageRequestedEvent) {
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
                flow = CONTENT_FLOW,
                stage = QUEUE_STAGE,
                postId = postId,
                attempt = null,
                outcome = ProcessingMetrics.Outcome.SUCCESS,
                duration = delay,
            ),
        )
    }

    private fun sendPush(postId: Long) {
        runCatching {
            sendPostProcessingPush(
                SendPostProcessingPushUseCase.Command(
                    postId = postId,
                    outcome = SendPostProcessingPushUseCase.Outcome.FAILED,
                ),
            )
        }.onFailure { exception ->
            logger.warn(exception) { "Post content failure push failed: postId=$postId" }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val CONTENT_FLOW = "post-content"
        const val MEDIA_FLOW = "post-media"
        const val QUEUE_STAGE = "queue"
        const val MEDIA_MAX_ATTEMPTS = 4
        const val DEFAULT_MEDIA_RETRY_BACKOFF_SECONDS = 3L
    }
}
