package org.every.nook.api.infrastructure.corepin

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.infrastructure.vision.VisionImageDownloader
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

class CorepinImageTextExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: CorepinOcrProperties,
    private val imageDownloader: VisionImageDownloader,
) : ImageTextExtractor {
    override fun extract(request: ImageTextExtractor.Request): List<ImageTranscript> {
        require(request.images.size == 1) { "Corepin OCR accepts one image per request" }
        require(properties.apiKey.isNotBlank()) { "Corepin OCR API key is not configured" }
        val startedAt = System.nanoTime()
        val image = request.images.single()
        val bytes = imageDownloader.download(image.imageUrl)
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("image", ImageResource(bytes, image.imageIndex))
            add("format", "text")
            add("max_tokens", MAX_TOKENS.toString())
        }
        val response = restClient.post()
            .uri("/v1/ocr")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: error("Corepin OCR returned an empty response")
        val root = objectMapper.readTree(response)
        val texts = root.path("text").asText().lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        logger.info(
            ProcessingLogEvent(
                action = "corepin.response.completed",
                flow = "place",
                stage = "image-transcript",
                outcome = "success",
                durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                fields = mapOf(
                    "provider.name" to "corepin",
                    "content.image_count" to 1,
                    "content.image_bytes" to bytes.size,
                    "ocr.transcript_count" to texts.size,
                ),
            ),
        )
        return listOf(ImageTranscript(image.imageIndex, texts))
    }

    private class ImageResource(bytes: ByteArray, imageIndex: Int) : ByteArrayResource(bytes) {
        private val filename = "image-$imageIndex.jpg"
        override fun getFilename(): String = filename
    }

    private companion object {
        val logger = LoggerFactory.getLogger(CorepinImageTextExtractor::class.java)
        const val MAX_TOKENS = 4096
        const val NANOS_PER_MILLISECOND = 1_000_000
    }
}
