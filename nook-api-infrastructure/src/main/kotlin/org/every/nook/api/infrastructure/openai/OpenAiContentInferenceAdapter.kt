package org.every.nook.api.infrastructure.openai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.post.PostTitleGenerator
import org.springframework.web.client.RestClient

class OpenAiContentInferenceAdapter(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: OpenAiProperties,
) : PostTitleGenerator,
    PlaceClueExtractor {
    override fun generate(request: PostTitleGenerator.Request): String {
        val result = requestStructured(
            name = "post_title",
            instructions = TITLE_INSTRUCTIONS,
            input = request.toInput(),
            schema = titleSchema(),
            maxOutputTokens = TITLE_MAX_OUTPUT_TOKENS,
        )
        return result.path("title").asText().trim().take(MAX_TITLE_LENGTH)
            .ifBlank { DEFAULT_TITLE }
    }

    override fun extract(request: PlaceClueExtractor.Request): List<PlaceClue> {
        val result = requestStructured(
            name = "place_clues",
            instructions = PLACE_INSTRUCTIONS,
            input = request.toInput(),
            schema = placeSchema(),
            maxOutputTokens = PLACE_MAX_OUTPUT_TOKENS,
        )
        return result.path("places").map { place ->
            PlaceClue(
                name = place.path("name").asText().trim(),
                region = place.path("region").takeUnless(JsonNode::isNull)?.asText()?.trim()?.ifBlank { null },
                queries = place.path("queries").map(JsonNode::asText).map(String::trim).filter(String::isNotEmpty),
            )
        }
    }

    private fun requestStructured(
        name: String,
        instructions: String,
        input: String,
        schema: Map<String, Any>,
        maxOutputTokens: Int,
    ): JsonNode {
        require(properties.apiKey.isNotBlank()) { "OpenAI API key is not configured" }
        val request = mapOf(
            "model" to properties.model,
            "instructions" to instructions,
            "input" to input,
            "reasoning" to mapOf("effort" to "minimal"),
            "max_output_tokens" to maxOutputTokens,
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to name,
                    "strict" to true,
                    "schema" to schema,
                ),
            ),
        )
        val response = restClient.post()
            .uri("/v1/responses")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .body(request)
            .retrieve()
            .body(String::class.java)
            ?: error("OpenAI returned an empty response")
        val root = objectMapper.readTree(response)
        val content = root.path("output")
            .flatMap { it.path("content").toList() }
        if (content.any { it.path("type").asText() == "refusal" }) {
            error("OpenAI refused the request")
        }
        val text = content.firstOrNull { it.path("type").asText() == "output_text" }
            ?.path("text")
            ?.asText()
            ?: error("OpenAI returned no structured output")
        return objectMapper.readTree(text).also { result ->
            logger.info { "OpenAI structured output received: name=$name, output=$result" }
        }
    }

    private fun PostTitleGenerator.Request.toInput(): String = contentInput(body, hashtags, sourceLocationTag)

    private fun PlaceClueExtractor.Request.toInput(): String = contentInput(body, hashtags, sourceLocationTag)

    private fun contentInput(body: String?, hashtags: List<String>, sourceLocationTag: String?): String =
        objectMapper.writeValueAsString(
            mapOf(
                "body" to body,
                "hashtags" to hashtags,
                "sourceLocationTag" to sourceLocationTag,
            ),
        )

    private fun titleSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string", "maxLength" to MAX_TITLE_LENGTH),
        ),
        "required" to listOf("title"),
        "additionalProperties" to false,
    )

    private fun placeSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "places" to mapOf(
                "type" to "array",
                "maxItems" to MAX_PLACE_COUNT,
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string"),
                        "region" to mapOf("type" to listOf("string", "null")),
                        "queries" to mapOf(
                            "type" to "array",
                            "minItems" to 1,
                            "maxItems" to MAX_QUERY_COUNT,
                            "items" to mapOf("type" to "string"),
                        ),
                    ),
                    "required" to listOf("name", "region", "queries"),
                    "additionalProperties" to false,
                ),
            ),
        ),
        "required" to listOf("places"),
        "additionalProperties" to false,
    )

    private companion object {
        val logger = KotlinLogging.logger {}

        const val MAX_TITLE_LENGTH = 25
        const val MAX_PLACE_COUNT = 10
        const val MAX_QUERY_COUNT = 3
        const val TITLE_MAX_OUTPUT_TOKENS = 200
        const val PLACE_MAX_OUTPUT_TOKENS = 800
        const val DEFAULT_TITLE = "Instagram 게시물"
        const val TITLE_INSTRUCTIONS =
            "입력된 Instagram 본문, 해시태그, 장소 태그에 명시된 사실만 사용해 검색과 보관에 적합한 한국어 제목을 작성한다. " +
                "제목은 최대 25자이며 지역명, 상호명, 업종, 장소 개수 중 확인 가능한 핵심 정보만 사용한다. " +
                "한 가게는 '중랑구 임현숙의이화김치찌개', 여러 가게는 '중곡동 맛집 5곳', " +
                "지역이 없으면 '홍별감네 외 맛집 5곳' 형식을 따른다. " +
                "투어, 감동, 보물, 한자리 같은 홍보성·감상적 표현과 따옴표, 해시태그를 사용하지 않는다. " +
                "정보가 부족하면 'Instagram 게시물'을 반환한다."
        const val PLACE_INSTRUCTIONS =
            "입력된 Instagram 본문, 해시태그, 장소 태그에 명시된 실제 방문 가게만 추출한다. " +
                "가게는 음식점, 카페, 술집, 상점, 숙박업소처럼 상호명이 있는 영업 장소를 뜻한다. " +
                "도시, 구, 동, 거리, 역, 공원, 관광지는 가게로 반환하지 말고 가게 검색을 위한 region과 query 단서로만 사용한다. " +
                "상호명이 확인되지 않으면 추측하거나 일반 업종명으로 만들지 않는다. 좌표와 주소도 만들지 않는다. " +
                "장소별 상호명 name, 확인 가능한 region, 카카오 장소 검색용 queries를 반환한다. " +
                "가게 근거가 없으면 places를 빈 배열로 반환한다. 최대 10개 가게와 가게당 최대 3개 검색어만 반환한다."
    }
}
