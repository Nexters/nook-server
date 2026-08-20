package org.every.nook.api.infrastructure.openai

import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class OpenAiImageTextExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: OpenAiProperties,
) : ImageTextExtractor {
    override fun extract(request: ImageTextExtractor.Request): List<ImageTranscript> {
        val startedAt = System.nanoTime()
        require(request.images.isNotEmpty()) { "At least one image is required" }
        require(request.images.size <= MAX_BATCH_SIZE) { "Too many images in transcript request" }
        require(properties.apiKey.isNotBlank()) { "OpenAI API key is not configured" }
        val response = restClient.post()
            .uri("/v1/responses")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .body(request.toOpenAiRequest())
            .retrieve()
            .body(String::class.java)
            ?: error("OpenAI returned an empty response")
        val root = objectMapper.readTree(response)
        val content = root.path("output").flatMap { it.path("content").toList() }
        if (content.any { it.path("type").asText() == "refusal" }) {
            error("OpenAI refused the request")
        }
        val text = content.firstOrNull { it.path("type").asText() == "output_text" }
            ?.path("text")
            ?.asText()
            ?: error("OpenAI returned no structured output")
        return objectMapper.readTree(text).path("images").toList().map { image ->
            ImageTranscript(
                imageIndex = image.path("imageIndex").asInt(),
                texts = image.path("texts").toList().map(JsonNode::asText).map(String::trim)
                    .filter(String::isNotEmpty),
            )
        }.also { transcripts ->
            logger.info(
                ProcessingLogEvent(
                    action = "openai.response.completed",
                    flow = "place",
                    stage = "image-transcript",
                    outcome = "success",
                    durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                    fields = mapOf(
                        "provider.name" to "openai",
                        "openai.model" to properties.imageTextModel,
                        "content.image_count" to request.images.size,
                        "ocr.transcript_count" to transcripts.size,
                    ),
                ),
            )
        }
    }

    private fun ImageTextExtractor.Request.toOpenAiRequest(): Map<String, Any> = mapOf(
        "model" to properties.imageTextModel,
        "instructions" to INSTRUCTIONS,
        "input" to listOf(
            mapOf(
                "role" to "user",
                "content" to buildList {
                    images.forEach { image ->
                        add(mapOf("type" to "input_text", "text" to "다음 이미지의 imageIndex는 ${image.imageIndex}이다."))
                        add(
                            mapOf(
                                "type" to "input_image",
                                "image_url" to image.imageUrl,
                                "detail" to IMAGE_DETAIL,
                            ),
                        )
                    }
                },
            ),
        ),
        "reasoning" to mapOf("effort" to "minimal"),
        "max_output_tokens" to MAX_OUTPUT_TOKENS,
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "image_transcripts",
                "strict" to true,
                "schema" to SCHEMA,
            ),
        ),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(OpenAiImageTextExtractor::class.java)
        const val NANOS_PER_MILLISECOND = 1_000_000
        const val MAX_BATCH_SIZE = 5
        const val MAX_IMAGE_COUNT = 20
        const val MAX_OUTPUT_TOKENS = 4000
        const val IMAGE_DETAIL = "high"
        const val INSTRUCTIONS =
            "각 입력 이미지에서 사람이 읽을 수 있는 모든 텍스트를 판단, 요약, 번역, 맞춤법 교정하거나 장소 정보로 " +
                "재구성하지 말고 보이는 그대로 전사한다. 상호명, 주소, 작은 설명, 영문, 숫자와 로고 텍스트를 포함한다. " +
                "텍스트는 읽기 단위별 문자열로 나누고 중복되어 보여도 생략하지 않는다. 읽을 수 없는 문자는 추측하지 않는다. " +
                "각 입력 이미지마다 앞에 제공된 imageIndex를 그대로 사용해 하나의 images 항목을 반드시 반환하며, " +
                "읽을 수 있는 텍스트가 없으면 texts를 빈 배열로 반환한다."
        val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "images" to mapOf(
                    "type" to "array",
                    "maxItems" to MAX_BATCH_SIZE,
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "imageIndex" to mapOf("type" to "integer", "minimum" to 1, "maximum" to MAX_IMAGE_COUNT),
                            "texts" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        ),
                        "required" to listOf("imageIndex", "texts"),
                        "additionalProperties" to false,
                    ),
                ),
            ),
            "required" to listOf("images"),
            "additionalProperties" to false,
        )
    }
}
