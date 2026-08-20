package org.every.nook.api.infrastructure.clova

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.clova-ocr")
data class ClovaOcrProperties(
    val invokeUrl: String = "",
    val secretKey: String = "",
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
    val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
) {
    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
        const val DEFAULT_MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }
}
