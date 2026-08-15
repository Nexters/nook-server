package org.every.nook.api.infrastructure.vision

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.google-cloud-vision")
data class GoogleCloudVisionProperties(
    val baseUrl: String = "https://vision.googleapis.com",
    val apiKey: String = "",
    val featureType: String = DEFAULT_FEATURE_TYPE,
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    private companion object {
        const val DEFAULT_FEATURE_TYPE = "DOCUMENT_TEXT_DETECTION"
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
    }
}
