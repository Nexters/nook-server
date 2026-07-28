package org.every.nook.api.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("post-content-parsing.worker")
data class PostContentParsingProperties(
    val retryBackoffs: List<Duration> = listOf(
        Duration.ofSeconds(DEFAULT_RETRY_SECONDS),
        Duration.ofSeconds(DEFAULT_RETRY_SECONDS),
        Duration.ofSeconds(DEFAULT_RETRY_SECONDS),
    ),
    val processingTimeout: Duration = Duration.ofMinutes(DEFAULT_PROCESSING_TIMEOUT_MINUTES),
) {
    init {
        require(retryBackoffs.size == RETRY_COUNT) { "Exactly $RETRY_COUNT retry backoffs are required" }
        require(retryBackoffs.all { !it.isNegative && !it.isZero }) { "Retry backoffs must be positive" }
        require(!processingTimeout.isNegative && !processingTimeout.isZero) { "Processing timeout must be positive" }
    }

    private companion object {
        const val RETRY_COUNT = 3
        const val DEFAULT_RETRY_SECONDS = 3L
        const val DEFAULT_PROCESSING_TIMEOUT_MINUTES = 15L
    }
}
