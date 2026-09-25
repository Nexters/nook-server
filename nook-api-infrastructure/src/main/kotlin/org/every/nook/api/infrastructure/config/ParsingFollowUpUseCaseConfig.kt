package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.post.StorePostMediaUseCase
import org.every.nook.api.application.processing.ParsingFailureClassifier
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.every.nook.api.application.processing.ProcessParsingFollowUpJobsUseCase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Duration
import java.util.concurrent.Executor

@Configuration
class ParsingFollowUpUseCaseConfig {
    @Bean("parsingFollowUpTaskExecutor")
    fun parsingFollowUpTaskExecutor(@Value("\${parsing.follow-up.concurrency:2}") concurrency: Int): Executor =
        ThreadPoolTaskExecutor().apply {
            require(concurrency > 0) { "Follow-up job concurrency must be positive" }
            corePoolSize = concurrency
            maxPoolSize = concurrency
            queueCapacity = concurrency
            setThreadNamePrefix("parsing-follow-up-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS)
            initialize()
        }

    @Bean
    fun processParsingFollowUpJobsUseCase(
        failureClassifier: ParsingFailureClassifier,
        jobPort: ParsingFollowUpJobPort,
        storePostMedia: StorePostMediaUseCase,
        storePlaceThumbnail: StorePlaceThumbnailUseCase,
        storePlaceTags: StorePlaceTagsUseCase,
        @Value("\${parsing.follow-up.batch-size:10}") batchSize: Int,
        @Value("\${parsing.follow-up.processing-timeout:5m}") processingTimeout: Duration,
        @Value("\${parsing.follow-up.retry-backoff:10s}") retryBackoff: Duration,
        @Value("\${parsing.follow-up.max-attempts:4}") maxAttempts: Int,
        @Value("\${parsing.follow-up.concurrency:2}") concurrency: Int,
        @Qualifier("parsingFollowUpTaskExecutor") executor: Executor,
    ): ProcessParsingFollowUpJobsUseCase = ProcessParsingFollowUpJobsUseCase(
        failureClassifier = failureClassifier,
        jobPort = jobPort,
        storePostMedia = storePostMedia,
        storePlaceThumbnail = storePlaceThumbnail,
        storePlaceTags = storePlaceTags,
        batchSize = batchSize,
        processingTimeout = processingTimeout,
        retryBackoff = retryBackoff,
        maxAttempts = maxAttempts,
        concurrency = concurrency,
        executor = executor,
    )

    private companion object {
        const val SHUTDOWN_TIMEOUT_SECONDS = 60
    }
}
