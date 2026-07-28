package org.every.nook.api.post

import mu.KotlinLogging
import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.every.nook.api.application.post.PostContentParsingJobRequestedEvent
import org.every.nook.api.application.post.ProcessPostContentParsingJobUseCase
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
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
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) {
    @EventListener(ApplicationReadyEvent::class)
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
        logger.info {
            "Post content parsing event received: postId=${event.postId}, availableAt=${event.availableAt}"
        }
        if (!waitUntil(event.availableAt)) {
            return
        }
        while (true) {
            when (val result = processPostContentParsingJob(event.postId)) {
                ProcessPostContentParsingJobUseCase.Result.Completed,
                ProcessPostContentParsingJobUseCase.Result.Failed,
                ProcessPostContentParsingJobUseCase.Result.Skipped,
                -> return

                is ProcessPostContentParsingJobUseCase.Result.Retry -> if (!waitUntil(result.nextAttemptAt)) {
                    return
                }
            }
        }
    }

    private fun waitUntil(availableAt: Instant?): Boolean {
        val target = availableAt ?: return true
        while (true) {
            val delay = Duration.between(clock.instant(), target)
            if (delay.isNegative || delay.isZero) {
                return true
            }
            try {
                Thread.sleep(delay)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn(exception) { "Post content parsing wait interrupted" }
                return false
            }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
