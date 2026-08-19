package org.every.nook.api.infrastructure.place

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.apify-naver-place")
data class ApifyNaverPlaceProperties(
    val baseUrl: String = "https://api.apify.com",
    val apiToken: String = "",
    val actorId: String = "delicious_zebu~naver-map-search-results-scraper",
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    init {
        require(maxResults in 1..MAX_RESULTS) { "Apify Naver place max results must be between 1 and $MAX_RESULTS" }
    }

    private companion object {
        const val DEFAULT_MAX_RESULTS = 5
        const val MAX_RESULTS = 20
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 120L
    }
}
