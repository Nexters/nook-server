package org.every.nook.api.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("place-parsing.worker")
data class PlaceParsingProperties(
    val retryBackoffs: List<Duration> = listOf(
        Duration.ofSeconds(RETRY_SECONDS),
        Duration.ofSeconds(RETRY_SECONDS),
        Duration.ofSeconds(RETRY_SECONDS),
    ),
    val processingTimeout: Duration = Duration.ofMinutes(1),
) {
    init {
        require(retryBackoffs.size == RETRY_COUNT) { "Exactly $RETRY_COUNT retry backoffs are required" }
        require(retryBackoffs.all { !it.isNegative && !it.isZero }) { "Retry backoffs must be positive" }
        require(!processingTimeout.isNegative && !processingTimeout.isZero) { "Processing timeout must be positive" }
    }

    private companion object {
        const val RETRY_COUNT = 3
        const val RETRY_SECONDS = 3L
    }
}
