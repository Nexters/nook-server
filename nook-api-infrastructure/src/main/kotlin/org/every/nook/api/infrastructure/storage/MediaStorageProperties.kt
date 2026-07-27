package org.every.nook.api.infrastructure.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("external.media-storage")
data class MediaStorageProperties(
    val enabled: Boolean = false,
    val bucket: String = "",
    val region: String = DEFAULT_REGION,
    val cloudFrontBaseUrl: String = "",
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
    val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
    val maxVideoBytes: Long = DEFAULT_MAX_VIDEO_BYTES,
    val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) {
    init {
        require(maxImageBytes > 0) { "Media storage max image bytes must be positive" }
        require(maxVideoBytes > 0) { "Media storage max video bytes must be positive" }
        require(maxRedirects >= 0) { "Media storage max redirects must not be negative" }
        if (enabled) {
            require(bucket.isNotBlank()) { "Media storage bucket must not be blank when enabled" }
            require(region.isNotBlank()) { "Media storage region must not be blank when enabled" }
            requireCloudFrontBaseUrl()
        }
    }

    private fun requireCloudFrontBaseUrl() {
        val uri = runCatching { URI(cloudFrontBaseUrl) }.getOrNull()
        require(uri?.scheme == HTTPS_SCHEME && !uri.host.isNullOrBlank()) {
            "Media storage CloudFront base URL must be an HTTPS URL when enabled"
        }
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val DEFAULT_REGION = "ap-northeast-2"
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
        const val DEFAULT_MAX_IMAGE_BYTES = 20L * 1024 * 1024
        const val DEFAULT_MAX_VIDEO_BYTES = 100L * 1024 * 1024
        const val DEFAULT_MAX_REDIRECTS = 3
    }
}
