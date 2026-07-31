package org.every.nook.api.infrastructure.place

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.google-place-photo")
data class GooglePlacePhotoProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://places.googleapis.com",
    val apiKey: String = "",
    val maxWidthPx: Int = DEFAULT_MAX_WIDTH_PX,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    init {
        require(maxWidthPx > 0) { "Google place photo max width must be positive" }
    }

    private companion object {
        const val DEFAULT_MAX_WIDTH_PX = 600
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 5L
    }
}
