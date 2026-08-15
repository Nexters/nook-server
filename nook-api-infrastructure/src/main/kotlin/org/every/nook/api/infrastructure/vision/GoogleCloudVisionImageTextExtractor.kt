package org.every.nook.api.infrastructure.vision

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class GoogleCloudVisionImageTextExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: GoogleCloudVisionProperties,
) : ImageTextExtractor {
    override fun extract(request: ImageTextExtractor.Request): List<ImageTranscript> {
        val startedAt = System.nanoTime()
        require(request.images.isNotEmpty()) { "At least one image is required" }
        require(request.images.size <= MAX_BATCH_SIZE) { "Too many images in transcript request" }
        require(properties.apiKey.isNotBlank()) { "Google Cloud Vision API key is not configured" }
        val response = restClient.post()
            .uri { builder -> builder.path("/v1/images:annotate").queryParam("key", properties.apiKey).build() }
            .body(request.toVisionRequest())
            .retrieve()
            .body(String::class.java)
            ?: error("Google Cloud Vision returned an empty response")
        val transcripts = objectMapper.readTree(response)
            .path("responses")
            .toList()
            .mapIndexed { index, annotation -> annotation.toTranscript(request.images[index].imageIndex) }

        logger.info(
            ProcessingLogEvent(
                action = "google-cloud-vision.response.completed",
                flow = "place",
                stage = "image-transcript",
                outcome = "success",
                durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                fields = mapOf(
                    "provider.name" to "google-cloud-vision",
                    "vision.feature_type" to properties.featureType,
                    "content.image_count" to request.images.size,
                    "ocr.transcript_count" to transcripts.size,
                ),
            ),
        )
        return transcripts
    }

    private fun ImageTextExtractor.Request.toVisionRequest(): Map<String, Any> = mapOf(
        "requests" to images.map { image ->
            mapOf(
                "image" to mapOf("source" to mapOf("imageUri" to image.imageUrl)),
                "features" to listOf(mapOf("type" to properties.featureType)),
                "imageContext" to mapOf("languageHints" to LANGUAGE_HINTS),
            )
        },
    )

    private fun JsonNode.toTranscript(imageIndex: Int): ImageTranscript {
        val error = path("error")
        if (!error.isMissingNode && error.path("message").asText().isNotBlank()) {
            error("Google Cloud Vision failed: ${error.path("message").asText()}")
        }
        val fullText = path("fullTextAnnotation").path("text").asText().trim()
            .ifBlank { path("textAnnotations").firstOrNull()?.path("description")?.asText().orEmpty().trim() }
        return ImageTranscript(
            imageIndex = imageIndex,
            texts = fullText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(GoogleCloudVisionImageTextExtractor::class.java)
        val LANGUAGE_HINTS = listOf("ko", "en", "ja")
        const val MAX_BATCH_SIZE = 5
        const val NANOS_PER_MILLISECOND = 1_000_000
    }
}
