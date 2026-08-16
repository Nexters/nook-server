package org.every.nook.api.infrastructure.vision

import org.springframework.web.client.RestClient

/**
 * Cloud Vision 에 넘길 이미지를 직접 내려받는다.
 *
 * Vision 의 `imageUri` 방식은 GCS 가 아닌 일반 HTTP URL 에 대해 best-effort 라
 * "We can not access the URL currently" 로 실패한다. 바이트를 실어 보내려면
 * 서버가 먼저 받아와야 한다.
 */
class VisionImageDownloader(private val restClient: RestClient, private val maxImageBytes: Long) {
    fun download(imageUrl: String): ByteArray {
        val bytes = runCatching {
            restClient.get().uri(imageUrl).retrieve().body(ByteArray::class.java)
        }.getOrElse { cause ->
            error("Failed to download image for Cloud Vision: $imageUrl (${cause.message})")
        } ?: error("Downloaded an empty image for Cloud Vision: $imageUrl")

        check(bytes.size <= maxImageBytes) {
            "Image is too large for Cloud Vision: $imageUrl (${bytes.size} bytes, limit $maxImageBytes)"
        }
        return bytes
    }
}
