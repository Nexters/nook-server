package org.every.nook.api.infrastructure.openai

import mu.KotlinLogging
import org.every.nook.api.application.billing.NoOpExternalApiUsageMeter
import org.every.nook.api.application.post.PostTitleSelector
import org.every.nook.api.infrastructure.billing.ExternalApiCallMeter
import org.every.nook.api.infrastructure.billing.SettledUsage
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

class OpenAiPostTitleSelector(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: OpenAiProperties,
    private val callMeter: ExternalApiCallMeter = ExternalApiCallMeter(NoOpExternalApiUsageMeter),
) : PostTitleSelector {
    override fun select(request: PostTitleSelector.Request): PostTitleSelector.Result {
        require(properties.apiKey.isNotBlank()) { "OpenAI API key is not configured" }
        val response = callMeter.measure(
            provider = "openai",
            sku = properties.model,
            feature = "post-title-selection",
            estimatedUnits = BigDecimal.valueOf(MAX_OUTPUT_TOKENS.toLong()),
            metadata = mapOf("model" to properties.model),
            usage = ::usage,
        ) {
            restClient.post().uri("/v1/responses")
                .header("Authorization", "Bearer ${properties.apiKey}")
                .body(request.toOpenAiRequest()).retrieve().body(String::class.java)
                ?: error("OpenAI returned an empty response")
        }
        val root = objectMapper.readTree(response)
        val content = root.path("output").flatMap { it.path("content").toList() }
        if (content.any { it.path("type").asText() == "refusal" }) {
            error("OpenAI refused the request")
        }
        val text = content.firstOrNull { it.path("type").asText() == "output_text" }
            ?.path("text")
            ?.asText()
            ?: error("OpenAI returned no structured output")
        return objectMapper.readTree(text).toResult().also { result ->
            logger.info {
                "OpenAI post title selected: source=${result.source}, " +
                    "rejectedCoverReason=${result.rejectedCoverReason}"
            }
        }
    }

    private fun PostTitleSelector.Request.toOpenAiRequest(): Map<String, Any> = mapOf(
        "model" to properties.model,
        "instructions" to INSTRUCTIONS,
        "input" to listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(mapOf("type" to "input_text", "text" to toInput())),
            ),
        ),
        "reasoning" to mapOf("effort" to "minimal"),
        "max_output_tokens" to MAX_OUTPUT_TOKENS,
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "post_title_selection",
                "strict" to true,
                "schema" to SCHEMA,
            ),
        ),
    )

    private fun PostTitleSelector.Request.toInput(): String = objectMapper.writeValueAsString(
        mapOf(
            "body" to body,
            "hashtags" to hashtags,
            "sourceLocationTag" to sourceLocationTag,
            "coverOcrTexts" to coverTexts,
            "declaredPlaceCount" to declaredPlaceCount,
            "resolvedPlaces" to places.map { place ->
                mapOf(
                    "name" to place.name,
                    "address" to place.address,
                    "city" to place.city,
                    "category" to place.category,
                )
            },
        ),
    )

    private fun JsonNode.toResult(): PostTitleSelector.Result = PostTitleSelector.Result(
        title = path("title").nullableText(),
        source = PostTitleSelector.Source.valueOf(path("source").asText()),
        evidence = path("evidence").toList().map(JsonNode::asText).map(String::trim),
        rejectedCoverReason = path("rejectedCoverReason").nullableText(),
    )

    private fun usage(response: String): SettledUsage {
        val usage = objectMapper.readTree(response).path("usage")
        val input = usage.path("input_tokens").asLong(0)
        val cached = usage.path("input_tokens_details").path("cached_tokens").asLong(0)
        val output = usage.path("output_tokens").asLong(0)
        return SettledUsage(BigDecimal.valueOf(input + output), input, cached, output)
    }

    private fun JsonNode.nullableText(): String? = takeUnless { isNull || isMissingNode }
        ?.asText()
        ?.trim()
        ?.ifBlank { null }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val MAX_OUTPUT_TOKENS = 800
        const val MAX_TITLE_LENGTH = 25
        const val MAX_EVIDENCE_COUNT = 5
        val SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("type" to listOf("string", "null"), "maxLength" to MAX_TITLE_LENGTH),
                "source" to mapOf("enum" to PostTitleSelector.Source.entries.map { it.name }),
                "evidence" to mapOf(
                    "type" to "array",
                    "maxItems" to MAX_EVIDENCE_COUNT,
                    "items" to mapOf("type" to "string", "maxLength" to 200),
                ),
                "rejectedCoverReason" to mapOf(
                    "type" to listOf("string", "null"),
                    "maxLength" to 200,
                ),
            ),
            "required" to listOf("title", "source", "evidence", "rejectedCoverReason"),
            "additionalProperties" to false,
        )
        const val INSTRUCTIONS =
            "Instagram 게시물의 최종 제목을 본문, 해시태그, 위치 태그, 첫 이미지 OCR과 확정된 장소 정보로 결정한다. " +
                "제목은 검색과 보관에 적합한 한국어 최대 25자이며 입력에서 확인되는 사실만 사용한다. " +
                "resolvedPlaces는 검색과 후보 검증을 통과한 장소이므로 상호명, 주소, city, category의 가장 강한 근거다. " +
                "본문의 명시적 첫 제목이나 핵심 주제와 resolvedPlaces의 정보를 우선하고 OCR은 약한 보조 근거로만 사용한다. " +
                "coverOcrTexts에는 서로 떨어진 문구가 한 문자열로 합쳐지거나 OCR 오타가 있을 수 있다. " +
                "PICK, VOL, 날짜, 회차, 계정명, 로고, 브랜드 배지, 장식 문구는 제목에 포함하지 않는다. " +
                "OCR이 본문 또는 확정 장소와 충돌하면 OCR을 버리고 rejectedCoverReason에 이유를 적는다. " +
                "한 장소는 상호명과 본문에 명시된 지역·대표 메뉴·주제를 조합할 수 있다. " +
                "여러 장소는 공통 지역, 본문 주제, 공통 업종을 우선한다. " +
                "장소 개수는 declaredPlaceCount가 null이거나 resolvedPlaces 개수와 같을 때만 resolvedPlaces 개수로 쓴다. " +
                "둘이 다르면 제목에서 개수를 생략하고 '모음'처럼 숫자가 없는 표현을 사용한다. " +
                "카페를 맛집으로 바꾸거나 여러 업종을 하나의 업종으로 추측하지 않는다. " +
                "홍보성·감상적 표현, 따옴표, 해시태그, 이모지를 사용하지 않는다. " +
                "근거가 부족하면 title은 null, source는 NONE으로 반환한다. " +
                "source는 제목을 주로 뒷받침한 BODY, COVER_OCR, RESOLVED_PLACES, COMBINED 중 하나다. " +
                "evidence에는 입력에 실제 존재하는 짧은 근거만 최대 5개 반환한다."
    }
}
