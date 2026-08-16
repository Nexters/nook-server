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
    /** 이미지 1장을 내려받을 때 허용하는 최대 크기. Cloud Vision 의 이미지당 20MB 제한보다 낮게 둔다. */
    val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
    /** 한 번의 annotate 요청에 실을 원본 바이트 합계 상한. base64 는 약 4/3 로 늘어나므로 여유를 둔다. */
    val maxRequestBytes: Long = DEFAULT_MAX_REQUEST_BYTES,
    val imageConnectTimeout: Duration = Duration.ofSeconds(DEFAULT_IMAGE_CONNECT_TIMEOUT_SECONDS),
    val imageReadTimeout: Duration = Duration.ofSeconds(DEFAULT_IMAGE_READ_TIMEOUT_SECONDS),
) {
    private companion object {
        const val DEFAULT_FEATURE_TYPE = "DOCUMENT_TEXT_DETECTION"
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
        const val DEFAULT_MAX_IMAGE_BYTES = 4L * 1024 * 1024
        const val DEFAULT_MAX_REQUEST_BYTES = 12L * 1024 * 1024
        const val DEFAULT_IMAGE_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_IMAGE_READ_TIMEOUT_SECONDS = 10L
    }
}
