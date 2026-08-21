package org.every.nook.api.infrastructure.place

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.apify-naver-place-photo")
data class ApifyNaverPlacePhotoProperties(
    val baseUrl: String = "https://api.apify.com",
    val apiToken: String = "",
    val searchActorId: String = "delicious_zebu~naver-map-search-results-scraper",
    val photoActorId: String = "oxygenated_quagmire~naver-place-photos",
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val storageConcurrency: Int = DEFAULT_STORAGE_CONCURRENCY,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    init {
        require(maxResults in 1..MAX_RESULTS) { "Apify Naver place max results must be between 1 and $MAX_RESULTS" }
        require(batchSize in 1..MAX_BATCH_SIZE) { "Apify Naver place batch size must be between 1 and $MAX_BATCH_SIZE" }
        require(storageConcurrency in 1..MAX_STORAGE_CONCURRENCY) {
            "Apify Naver place storage concurrency must be between 1 and $MAX_STORAGE_CONCURRENCY"
        }
    }

    private companion object {
        const val DEFAULT_MAX_RESULTS = 5
        const val MAX_RESULTS = 20
        const val DEFAULT_BATCH_SIZE = 20
        const val MAX_BATCH_SIZE = 20
        const val DEFAULT_STORAGE_CONCURRENCY = 6
        const val MAX_STORAGE_CONCURRENCY = 12
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 300L
    }
}
