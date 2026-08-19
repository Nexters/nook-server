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
                    mapOf("type" to "input_text", "text" to "표지의 titleLabel과 title을 정확히 전사하세요."),
                    mapOf("type" to "input_image", "image_url" to imageUrl, "detail" to IMAGE_DETAIL),
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
                "schema" to SCHEMA,
            ),
        ),
    )

    private fun JsonNode.toCoverTitle(): String? {
        val title = path("title").nullableText() ?: return null
        val titleLabel = path("titleLabel").nullableText()
        return listOfNotNull(titleLabel, title)
            .joinToString(" ")
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank { null }
    }

    private fun JsonNode.nullableText(): String? = takeUnless { isNull || isMissingNode }
        ?.asText()
        ?.trim()
        ?.ifBlank { null }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 300
        const val MAX_TITLE_LENGTH = 500
        const val IMAGE_DETAIL = "high"
        const val INSTRUCTIONS =
            "Instagram 표지 이미지의 제목 영역을 글자 그대로 전사한다. " +
                "titleLabel은 제목 바로 위에 붙은 날짜·회차 라벨만, title은 게시물 전체를 설명하는 중앙의 큰 제목 문구만 반환한다. " +
                "위에서 아래로 읽고 줄바꿈은 공백으로 합친다. NEW 같은 배지와 계정명·로고는 제외한다. " +
                "추측, 요약, 번역, 맞춤법 교정, 바꿔쓰기를 금지한다. 해당 문구가 없으면 각 필드를 null로 반환한다."
        val WHITESPACE_PATTERN = Regex("\\s+")
        val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "titleLabel" to mapOf("type" to listOf("string", "null")),
                "title" to mapOf("type" to listOf("string", "null")),
            ),
            "required" to listOf("titleLabel", "title"),
            "additionalProperties" to false,
        )
    }
}
