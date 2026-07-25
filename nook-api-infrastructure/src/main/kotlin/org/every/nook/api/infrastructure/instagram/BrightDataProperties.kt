package org.every.nook.api.infrastructure.instagram

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.bright-data")
data class BrightDataProperties(
    val baseUrl: String = "https://api.brightdata.com",
    val apiToken: String = "",
    val postsDatasetId: String = "gd_lk5ns7kz21pck8jpis",
    val reelsDatasetId: String = "gd_lyclm20il4r5helnj",
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
    }
}
