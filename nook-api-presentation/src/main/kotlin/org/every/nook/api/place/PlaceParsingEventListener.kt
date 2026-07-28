package org.every.nook.api.place

import mu.KotlinLogging
import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.place.ProcessPlaceParsingJobUseCase
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
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
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) {
    @EventListener(ApplicationReadyEvent::class)
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

    @Async("placeParsingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun process(event: PlaceParsingJobRequestedEvent) {
        logger.info {
            "Place parsing event received: postId=${event.postId}, availableAt=${event.availableAt}"
        }
        if (!waitUntil(event.availableAt)) {
            return
        }
        while (true) {
            when (val result = processPlaceParsingJob(event.postId)) {
                ProcessPlaceParsingJobUseCase.Result.Completed,
                ProcessPlaceParsingJobUseCase.Result.Failed,
                ProcessPlaceParsingJobUseCase.Result.Skipped,
                -> return

                is ProcessPlaceParsingJobUseCase.Result.Retry -> if (!waitUntil(result.nextAttemptAt)) {
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
            logger.info {
                "Place parsing waiting for backoff or timeout: delayMs=${delay.toMillis()}, availableAt=$target"
            }
            try {
                Thread.sleep(delay)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn(exception) { "Place parsing wait interrupted" }
                return false
            }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
