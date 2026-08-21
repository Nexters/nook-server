package org.every.nook.api.infrastructure.openai

import org.every.nook.api.application.post.CoverTitleExtractor
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class OpenAiCoverTitleExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: OpenAiProperties,
) : CoverTitleExtractor {
    override fun extract(request: CoverTitleExtractor.Request): String? {
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
        return objectMapper.readTree(text).toCoverTitle()
    }

    private fun CoverTitleExtractor.Request.toOpenAiRequest(): Map<String, Any> = mapOf(
        "model" to properties.model,
        "instructions" to INSTRUCTIONS,
        "input" to listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "input_text", "text" to objectMapper.writeValueAsString(texts)),
                ),
            ),
        ),
        "reasoning" to mapOf("effort" to "minimal"),
        "max_output_tokens" to MAX_OUTPUT_TOKENS,
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "post_cover_title",
                "strict" to true,
                "schema" to schema(texts),
            ),
        ),
    )

    private fun JsonNode.toCoverTitle(): String? = path("title").nullableText()

    private fun JsonNode.nullableText(): String? = takeUnless { isNull || isMissingNode }
        ?.asText()
        ?.trim()
        ?.ifBlank { null }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 300
        const val INSTRUCTIONS =
            "입력은 Instagram 첫 이미지에서 OCR로 전사한 문자열 배열이다. " +
                "게시물 전체를 설명하는 명시적인 표지 제목 문구가 배열에 그대로 존재할 때만 하나를 선택한다. " +
                "날짜, 회차, VOL, PICK, 계정명, 로고, 배지, 장소 카드의 상호명, 주소, 사진 설명은 선택하지 않는다. " +
                "문구를 합치거나 수정하거나 새로 만들지 않는다. 명시적인 제목이 없거나 확실하지 않으면 null을 반환한다."

        fun schema(texts: List<String>): Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("enum" to texts.distinct() + null),
            ),
            "required" to listOf("title"),
            "additionalProperties" to false,
        )
    }
}
