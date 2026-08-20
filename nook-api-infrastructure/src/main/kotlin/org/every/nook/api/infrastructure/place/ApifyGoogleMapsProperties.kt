package org.every.nook.api.infrastructure.place

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.apify-google-maps")
data class ApifyGoogleMapsProperties(
    val baseUrl: String = "https://api.apify.com",
    val apiToken: String = "",
    val actorId: String = "compass~crawler-google-places",
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val storageConcurrency: Int = DEFAULT_STORAGE_CONCURRENCY,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    init {
        require(batchSize in 1..MAX_BATCH_SIZE) { "Apify Google Maps batch size must be between 1 and $MAX_BATCH_SIZE" }
        require(storageConcurrency in 1..MAX_STORAGE_CONCURRENCY) {
            "Apify Google Maps storage concurrency must be between 1 and $MAX_STORAGE_CONCURRENCY"
        }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 20
        const val MAX_BATCH_SIZE = 20
        const val DEFAULT_STORAGE_CONCURRENCY = 6
        const val MAX_STORAGE_CONCURRENCY = 12
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 300L
    }
}
