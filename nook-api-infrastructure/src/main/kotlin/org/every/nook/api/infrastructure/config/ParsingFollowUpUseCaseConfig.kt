package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.post.StorePostMediaUseCase
import org.every.nook.api.application.processing.ParsingFollowUpJobPort
import org.every.nook.api.application.processing.ProcessParsingFollowUpJobsUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class ParsingFollowUpUseCaseConfig {
    @Bean
    fun processParsingFollowUpJobsUseCase(
        jobPort: ParsingFollowUpJobPort,
        storePostMedia: StorePostMediaUseCase,
        storePlaceThumbnail: StorePlaceThumbnailUseCase,
        storePlaceTags: StorePlaceTagsUseCase,
        @Value("\${parsing.follow-up.batch-size:10}") batchSize: Int,
        @Value("\${parsing.follow-up.processing-timeout:5m}") processingTimeout: Duration,
        @Value("\${parsing.follow-up.retry-backoff:10s}") retryBackoff: Duration,
        @Value("\${parsing.follow-up.max-attempts:4}") maxAttempts: Int,
    ): ProcessParsingFollowUpJobsUseCase = ProcessParsingFollowUpJobsUseCase(
        jobPort = jobPort,
        storePostMedia = storePostMedia,
        storePlaceThumbnail = storePlaceThumbnail,
        storePlaceTags = storePlaceTags,
        batchSize = batchSize,
        processingTimeout = processingTimeout,
        retryBackoff = retryBackoff,
        maxAttempts = maxAttempts,
    )
}
