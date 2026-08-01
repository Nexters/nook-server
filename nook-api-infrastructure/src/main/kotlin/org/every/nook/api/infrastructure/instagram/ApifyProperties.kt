package org.every.nook.api.infrastructure.instagram

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.apify")
data class ApifyProperties(
    val baseUrl: String = "https://api.apify.com",
    val apiToken: String = "",
    val actorId: String = "shu8hvrXbJbY3Eb9W",
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 120L
    }
}
