package org.every.nook.api.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("place-parsing.worker")
data class PlaceParsingProperties(
    val retryBackoffs: List<Duration> = listOf(
        Duration.ofSeconds(FIRST_RETRY_SECONDS),
        Duration.ofSeconds(SECOND_RETRY_SECONDS),
        Duration.ofSeconds(THIRD_RETRY_SECONDS),
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
        const val FIRST_RETRY_SECONDS = 5L
        const val SECOND_RETRY_SECONDS = 15L
        const val THIRD_RETRY_SECONDS = 45L
    }
}
