package org.every.nook.api.infrastructure.clova

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.infrastructure.vision.VisionImageDownloader
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.Base64
import java.util.UUID

class ClovaImageTextExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: ClovaOcrProperties,
    private val imageDownloader: VisionImageDownloader,
    private val clock: Clock = Clock.systemUTC(),
) : ImageTextExtractor {
    override fun extract(request: ImageTextExtractor.Request): List<ImageTranscript> {
        require(request.images.size == 1) { "CLOVA OCR accepts one image per request" }
        require(properties.invokeUrl.isNotBlank() && properties.secretKey.isNotBlank()) {
            "CLOVA OCR is not configured"
        }
        val startedAt = System.nanoTime()
        val image = request.images.single()
        val bytes = imageDownloader.download(image.imageUrl)
        val response = restClient.post()
            .uri(properties.invokeUrl)
            .header("X-OCR-SECRET", properties.secretKey)
            .body(
                mapOf(
                    "version" to "V2",
                    "requestId" to UUID.randomUUID().toString(),
                    "timestamp" to clock.millis(),
                    "lang" to "ko",
                    "images" to listOf(
                        mapOf(
                            "format" to imageFormat(bytes),
                            "name" to "image-${image.imageIndex}",
                            "data" to Base64.getEncoder().encodeToString(bytes),
                        ),
                    ),
                    "enableTableDetection" to false,
                ),
            )
            .retrieve()
            .body(String::class.java)
            ?: error("CLOVA OCR returned an empty response")
        val result = objectMapper.readTree(response).path("images").firstOrNull()
            ?: error("CLOVA OCR returned no image result")
        check(result.path("inferResult").asText() == "SUCCESS") {
            "CLOVA OCR failed: ${result.path("message").asText()}"
        }
        val texts = result.path("fields").toList().map { it.pathInferText() }.filter(String::isNotEmpty)
        logger.info(
            ProcessingLogEvent(
                action = "clova.response.completed",
                flow = "place",
                stage = "image-transcript",
                outcome = "success",
                durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                fields = mapOf(
                    "provider.name" to "clova",
                    "content.image_count" to 1,
                    "content.image_bytes" to bytes.size,
                    "ocr.transcript_count" to texts.size,
                ),
            ),
        )
        return listOf(ImageTranscript(image.imageIndex, texts))
    }

    private fun JsonNode.pathInferText(): String = path("inferText").asText().trim()

    private fun imageFormat(bytes: ByteArray): String = when {
        bytes.size >= PNG_SIGNATURE.size &&
            bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE) -> "png"

        else -> "jpg"
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ClovaImageTextExtractor::class.java)
        val PNG_SIGNATURE: ByteArray = Base64.getDecoder().decode("iVBORw0KGgo=")
        const val NANOS_PER_MILLISECOND = 1_000_000
    }
}
