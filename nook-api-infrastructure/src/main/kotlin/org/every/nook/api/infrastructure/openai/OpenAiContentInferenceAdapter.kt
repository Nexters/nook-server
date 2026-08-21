package org.every.nook.api.infrastructure.openai

import mu.KotlinLogging
import org.every.nook.api.application.place.InferredPlaceTag
import org.every.nook.api.application.place.PlaceCandidateSelector
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceClueEvidence
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceTagEvidenceSource
import org.every.nook.api.application.place.PlaceTagExtractor
import org.every.nook.api.application.post.PostContentInference
import org.every.nook.api.application.post.PostTitleInference
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.domain.place.PlaceTagDefinition
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class OpenAiContentInferenceAdapter(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: OpenAiProperties,
) : PostContentInference,
    PostTitleInference,
    PlaceClueExtractor,
    PlaceCandidateSelector,
    PlaceTagExtractor {
    override fun infer(request: PostContentInference.Request): PostContentInference.Inference {
        val result = requestStructured(
            name = "post_content_inference",
            instructions = CONTENT_INFERENCE_INSTRUCTIONS,
            input = request.toInput(),
            schema = contentInferenceSchema,
            maxOutputTokens = CONTENT_INFERENCE_MAX_OUTPUT_TOKENS,
        )
        return PostContentInference.Inference(
            title = result.path("title")
                .takeUnless { it.isNull || it.isMissingNode }
                ?.asText()
                ?.trim()
                ?.take(MAX_TITLE_LENGTH)
                .orEmpty(),
            placeClues = result.toPlaceClues(),
        )
    }

    override fun infer(request: PostTitleInference.Request): String? {
        val result = requestStructured(
            name = "post_title_inference",
            instructions = TITLE_INSTRUCTIONS,
            input = contentInput(objectMapper, request.body, request.hashtags, request.sourceLocationTag),
            schema = titleInferenceSchema,
            maxOutputTokens = TITLE_INFERENCE_MAX_OUTPUT_TOKENS,
        )
        return result.path("title")
            .takeUnless { it.isNull || it.isMissingNode }
            ?.asText()
            ?.trim()
            ?.ifBlank { null }
    }

    override fun extract(request: PlaceClueExtractor.Request): List<PlaceClue> {
        val result = requestStructured(
            name = "place_clues",
            instructions = PLACE_INSTRUCTIONS,
            input = request.toInput(),
            schema = placeSchema,
            maxOutputTokens = if (request.imageTranscripts.isEmpty()) {
                PLACE_MAX_OUTPUT_TOKENS
            } else {
                IMAGE_PLACE_MAX_OUTPUT_TOKENS
            },
        )
        return result.toPlaceClues()
    }

    private fun JsonNode.toPlaceClues(): List<PlaceClue> = path("places").toList().map { place ->
        PlaceClue(
            name = place.path("name").asText().trim(),
            region = place.path("region").takeUnless(JsonNode::isNull)?.asText()?.trim()?.ifBlank { null },
            queries = place.path("queries").toList().map(JsonNode::asText).map(String::trim)
                .filter(String::isNotEmpty),
            evidence = place.path("evidence").toList().map { evidence ->
                PlaceClueEvidence(
                    imageIndex = evidence.path("imageIndex").asInt(),
                    evidenceText = evidence.path("evidenceText").asText().trim(),
                )
            },
            addressHint = place.path("addressHint")
                .takeUnless { it.isNull || it.isMissingNode }
                ?.asText()
                ?.trim()
                ?.ifBlank { null },
        )
    }

    override fun select(request: PlaceCandidateSelector.Request): org.every.nook.api.application.place.PlaceCandidate? {
        val result = requestStructured(
            name = "place_candidate_selection",
            instructions = CANDIDATE_SELECTION_INSTRUCTIONS,
            input = request.toInput(),
            schema = candidateSelectionSchema(request.candidates.lastIndex),
            maxOutputTokens = CANDIDATE_SELECTION_MAX_OUTPUT_TOKENS,
        )
        val selectedIndex = result.path("candidateIndex")
            .takeUnless(JsonNode::isNull)
            ?.asInt()
            ?: return null
        return request.candidates.getOrNull(selectedIndex)?.place
            ?: error("OpenAI selected an unknown place candidate")
    }

    override fun extract(request: PlaceTagExtractor.Request): List<PlaceTagExtractor.Result> {
        require(request.places.all { it.candidateTags.isNotEmpty() }) { "Place tag candidates must not be empty" }
        val result = requestStructured(
            name = "place_tags",
            instructions = PLACE_TAG_INSTRUCTIONS,
            input = request.toInput(objectMapper),
            schema = placeTagSchema(request.places),
            maxOutputTokens = (request.places.size * PLACE_TAG_MAX_OUTPUT_TOKENS_PER_PLACE)
                .coerceIn(PLACE_TAG_MIN_OUTPUT_TOKENS, PLACE_TAG_MAX_OUTPUT_TOKENS),
        )
        return result.path("places").toList().map { place ->
            PlaceTagExtractor.Result(
                placeIndex = place.path("placeIndex").asInt(),
                tags = place.path("tags").toList().map { tag ->
                    InferredPlaceTag(
                        tag = tag.path("tag").asText(),
                        confidence = tag.path("confidence").asDouble(),
                        evidenceSource = PlaceTagEvidenceSource.valueOf(tag.path("evidenceSource").asText()),
                        evidenceText = tag.path("evidenceText").asText().trim(),
                    )
                },
            )
        }
    }

    private fun requestStructured(
        name: String,
        instructions: String,
        input: Any,
        schema: Map<String, Any>,
        maxOutputTokens: Int,
    ): JsonNode {
        val startedAt = System.nanoTime()
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
            eventLogger.info(
                ProcessingLogEvent(
                    action = "openai.response.completed",
                    flow = "content-inference",
                    stage = name,
                    outcome = "success",
                    durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                    fields = mapOf("provider.name" to "openai", "openai.model" to properties.model),
                ),
            )
        }
    }

    private fun PostContentInference.Request.toInput(): String =
        contentInput(objectMapper, body, hashtags, sourceLocationTag)

    private fun PlaceClueExtractor.Request.toInput(): String = objectMapper.writeValueAsString(
        mapOf(
            "body" to body,
            "hashtags" to hashtags,
            "sourceLocationTag" to sourceLocationTag,
            "imageTranscripts" to imageTranscripts.map { transcript ->
                mapOf(
                    "imageIndex" to transcript.imageIndex,
                    "texts" to transcript.texts,
                )
            },
        ),
    )

    private fun PlaceCandidateSelector.Request.toInput(): String = objectMapper.writeValueAsString(
        mapOf(
            "placeClue" to mapOf(
                "name" to clue.name,
                "region" to clue.region,
                "addressHint" to clue.addressHint,
                "queries" to clue.queries,
                "evidence" to clue.evidence.map { evidence ->
                    mapOf(
                        "imageIndex" to evidence.imageIndex,
                        "evidenceText" to evidence.evidenceText,
                    )
                },
            ),
            "candidates" to candidates.mapIndexed { index, candidate ->
                mapOf(
                    "candidateIndex" to index,
                    "provider" to candidate.place.provider,
                    "name" to candidate.place.name,
                    "address" to candidate.place.address,
                    "category" to candidate.place.category,
                    "matchedQueries" to candidate.matchedQueries,
                )
            },
        ),
    )

    private fun candidateSelectionSchema(lastCandidateIndex: Int): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "candidateIndex" to mapOf(
                "type" to listOf("integer", "null"),
                "minimum" to 0,
                "maximum" to lastCandidateIndex,
            ),
        ),
        "required" to listOf("candidateIndex"),
        "additionalProperties" to false,
    )

    private companion object {
        val logger = KotlinLogging.logger {}
        val eventLogger = LoggerFactory.getLogger(OpenAiContentInferenceAdapter::class.java)
        const val NANOS_PER_MILLISECOND = 1_000_000

        const val MAX_TITLE_LENGTH = 25
        const val MAX_PLACE_COUNT = 60
        const val MAX_QUERY_COUNT = 4
        const val CONTENT_INFERENCE_MAX_OUTPUT_TOKENS = 8000
        const val TITLE_INFERENCE_MAX_OUTPUT_TOKENS = 300
        const val PLACE_MAX_OUTPUT_TOKENS = 2500
        const val IMAGE_PLACE_MAX_OUTPUT_TOKENS = 12000
        const val CANDIDATE_SELECTION_MAX_OUTPUT_TOKENS = 100
        const val PLACE_TAG_MIN_OUTPUT_TOKENS = 600
        const val PLACE_TAG_MAX_OUTPUT_TOKENS_PER_PLACE = 500
        const val PLACE_TAG_MAX_OUTPUT_TOKENS = 5000
        val placeListSchema: Map<String, Any> = mapOf(
            "type" to "array",
            "maxItems" to MAX_PLACE_COUNT,
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string"),
                    "region" to mapOf("type" to listOf("string", "null")),
                    "addressHint" to mapOf("type" to listOf("string", "null")),
                    "queries" to mapOf(
                        "type" to "array",
                        "minItems" to 1,
                        "maxItems" to MAX_QUERY_COUNT,
                        "items" to mapOf("type" to "string"),
                    ),
                    "evidence" to mapOf(
                        "type" to "array",
                        "maxItems" to MAX_IMAGE_COUNT,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "imageIndex" to mapOf(
                                    "type" to "integer",
                                    "minimum" to 1,
                                    "maximum" to MAX_IMAGE_COUNT,
                                ),
                                "evidenceText" to mapOf("type" to "string"),
                            ),
                            "required" to listOf("imageIndex", "evidenceText"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
                "required" to listOf("name", "region", "addressHint", "queries", "evidence"),
                "additionalProperties" to false,
            ),
        )
        val contentInferenceSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("type" to listOf("string", "null"), "maxLength" to MAX_TITLE_LENGTH),
                "places" to placeListSchema,
            ),
            "required" to listOf("title", "places"),
            "additionalProperties" to false,
        )
        val titleInferenceSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "title" to mapOf("type" to listOf("string", "null"), "maxLength" to MAX_TITLE_LENGTH),
            ),
            "required" to listOf("title"),
            "additionalProperties" to false,
        )
        val placeSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf("places" to placeListSchema),
            "required" to listOf("places"),
            "additionalProperties" to false,
        )
        const val TITLE_INSTRUCTIONS =
            "입력된 Instagram 본문, 해시태그, 장소 태그에 명시된 사실만 사용해 검색과 보관에 적합한 한국어 제목을 작성한다. " +
                "제목은 최대 25자이며 지역명, 상호명, 업종, 장소 개수 중 확인 가능한 핵심 정보만 사용한다. " +
                "한 가게는 '중랑구 임현숙의이화김치찌개', 여러 가게는 '중곡동 맛집 5곳', " +
                "지역이나 상호 정보가 부족하면 제목을 만들지 않는다. " +
                "본문에 지역, 업종, 장소 개수가 명시되면 그 표현과 숫자를 그대로 유지한다. 카페를 맛집으로 바꾸지 않는다. " +
                "sourceLocationTag는 지역 문맥으로만 참고하고 상호명이나 고유 장소명으로 제목에 사용하지 않는다. " +
                "입력에 0개라고 명시되지 않은 한 제목에 0곳을 사용하지 않는다. " +
                "투어, 감동, 보물, 한자리 같은 홍보성·감상적 표현과 따옴표, 해시태그를 사용하지 않는다. " +
                "정보가 부족하면 null을 반환한다."
        const val PLACE_INSTRUCTIONS =
            "입력된 Instagram 본문, 해시태그, 장소 태그와 imageTranscripts에서 게시물이 방문, 소개 또는 추천하는 " +
                "실제 영업 장소만 추출한다. " +
                "가게는 음식점, 카페, 술집, 상점, 숙박업소처럼 상호명이 있는 영업 장소를 뜻한다. " +
                "도시, 구, 동, 거리, 역, 공원, 관광지는 가게로 반환하지 말고 가게 검색을 위한 region과 query 단서로만 사용한다. " +
                "상호명이 확인되지 않으면 추측하거나 일반 업종명으로 만들지 않는다. 좌표는 만들지 않는다. " +
                "본문이나 imageTranscripts에 주소가 명시된 경우에만 addressHint에 주소 원문 전체를 그대로 담고, " +
                "주소가 없으면 null을 반환한다. 주소를 축약하거나 보정하거나 추측하지 않는다. " +
                "특히 1층, 4층, B1, 지하 1층, 201호, 건물명, 출입구 같은 상세 위치를 절대 생략하지 않는다. " +
                "sourceLocationTag는 지역 문맥과 검색어를 보조하는 힌트로만 사용한다. " +
                "sourceLocationTag를 name, addressHint 또는 장소 존재의 근거로 사용하지 않는다. " +
                "name은 본문, 해시태그 또는 imageTranscripts에 명시된 상호명만 사용한다. " +
                "본문에서 상호명이 해시태그, 첫 문장 또는 소개 문장에 있고 '위치', '주소', 'add.'로 시작하는 주소가 " +
                "뒤쪽의 별도 문단에 있더라도, 다른 장소가 명시되지 않았다면 같은 영업 장소의 name과 addressHint로 연결한다. " +
                "주소가 없거나 여러 상호명 중 어느 주소인지 불명확하면 해시태그만으로 장소를 만들지 않는다. " +
                "상호명이 한 글자여도 주소 바로 앞에 명시되어 있으면 그대로 name으로 사용하고 일반 업종명으로 바꾸지 않는다. " +
                "imageTranscripts는 이미지별로 별도 전사된 원문이므로 이미지별 텍스트를 독립적으로 확인한다. " +
                "응답하기 전에 제공된 모든 imageIndex를 순서와 무관하게 한 번씩 검토하고, 상호명과 주소처럼 " +
                "영업 장소를 식별할 근거가 있는 이미지를 일부만 선택해 생략하지 않는다. " +
                "텍스트의 위치, 크기, 번호, 카드 형식이나 이미지당 장소 개수를 가정하지 않는다. " +
                "한 이미지에서 장소가 없거나 여러 개일 수 있고, 같은 장소가 여러 이미지에 나오면 하나로 합친다. " +
                "표지 제목, 장소 개수 문구, 지역명 또는 일반 업종명만으로 상호명을 만들지 않는다. " +
                "주소가 있는 장소 카드에서는 주소 바로 앞의 상호명 표기를 우선하며 OCR 오타로 보여도 임의 교정하지 않는다. " +
                "이미지 근거가 있는 장소는 imageTranscripts의 imageIndex와 상호명 또는 주소가 포함된 실제 전사 문구를 " +
                "evidenceText로 evidence에 담는다. 이미지가 없거나 이미지 근거가 아니면 evidence는 빈 배열이다. " +
                "읽을 수 없는 글씨나 로고를 추측하지 않는다. " +
                "장소별 상호명 name, 확인 가능한 region, 명시된 전체 주소 addressHint, 장소 검색용 queries를 반환한다. " +
                "queries는 본문에서 확인되는 한글·영문 표기와 지역 조합을 우선해 서로 다른 검색어 3~4개를 만든다. " +
                "sourceLocationTag는 상호명과 결합한 지역 검색어에만 사용할 수 있다. " +
                "addressHint가 있으면 상호명과 전체 주소를 조합한 검색어를 포함하고 층·호 정보를 그대로 유지한다. " +
                "가게 근거가 없으면 places를 빈 배열로 반환한다. 최대 60개 가게와 가게당 최대 4개 검색어만 반환한다."
        const val CONTENT_INFERENCE_INSTRUCTIONS =
            "title과 places를 하나의 응답으로 함께 반환한다. " + TITLE_INSTRUCTIONS + " " + PLACE_INSTRUCTIONS
        const val CANDIDATE_SELECTION_INSTRUCTIONS =
            "placeClue는 Instagram 게시물에서 추출한 장소 단서이고 candidates는 실제 장소 검색 결과다. " +
                "상호명의 한글·영문 표기, 숫자와 띄어쓰기 변형, 업종, addressHint, 후보 주소, region, " +
                "이미지 evidence, matchedQueries를 함께 비교해 " +
                "게시물이 가리키는 장소와 가장 일치하는 candidateIndex 하나를 선택한다. " +
                "도로명과 건물 번호가 다르거나 양쪽에 명시된 층·호가 충돌하면 선택하지 않는다. " +
                "도로명 주소만 같고 상호명이 다른 후보를 같은 건물이라는 이유로 선택하지 않는다. " +
                "후보에 없는 장소를 만들거나 후보 정보를 수정하지 않는다. " +
                "명확한 근거가 없거나 서로 다른 후보를 하나로 확정할 수 없으면 candidateIndex를 null로 반환한다."
        const val PLACE_TAG_INSTRUCTIONS =
            "하나의 Instagram 게시물에 포함된 여러 장소가 places로 입력된다. 각 placeIndex마다 결과를 정확히 하나 반환한다. " +
                "해당 장소의 본문 구간과 원본 해시태그에 명시된 사실만 사용해 candidateTags 중 실제 근거가 있는 " +
                "장소 특성 태그만 최대 4개 선택한다. 후보 밖의 태그는 만들지 않는다. " +
                "candidateTags의 keywords는 후보를 찾기 위한 예시이며 문맥상 부정되거나 다른 대상을 설명하면 선택하지 않는다. " +
                "장소명, 주소, 카테고리만으로 특성을 추측하지 않는다. " +
                "FRIENDLY처럼 경험이 필요한 서비스 태그는 본문이나 원본 해시태그에 직접 근거가 있을 때만 선택한다. " +
                "근거 우선순위는 본문, 해시태그 순이다. 각 태그에 0부터 1 사이 confidence, " +
                "BODY·HASHTAG 중 evidenceSource, 원문에서 그대로 인용한 실제 근거 문구만 evidenceText로 반환한다. " +
                "근거가 없다고 설명하기 위한 태그는 결과에 절대 포함하지 말고 해당 태그 자체를 생략한다. " +
                "같은 의미나 서로 모순되는 태그를 함께 선택하지 않고, 근거가 부족하면 tags를 비워 두며 4개를 강제로 채우지 않는다."
    }
}

private fun contentInput(
    objectMapper: ObjectMapper,
    body: String?,
    hashtags: List<String>,
    sourceLocationTag: String?,
): String = objectMapper.writeValueAsString(
    mapOf("body" to body, "hashtags" to hashtags, "sourceLocationTag" to sourceLocationTag),
)

private fun PlaceTagExtractor.Request.toInput(objectMapper: ObjectMapper): String = objectMapper.writeValueAsString(
    mapOf(
        "places" to places.map { input ->
            mapOf(
                "placeIndex" to input.placeIndex,
                "place" to mapOf(
                    "name" to input.place.name,
                    "address" to input.place.address,
                    "category" to input.place.category,
                ),
                "body" to input.body,
                "hashtags" to input.hashtags,
                "candidateTags" to input.candidateTags.map { tag ->
                    mapOf(
                        "tag" to tag.tag,
                        "category" to tag.category.name,
                        "displayName" to tag.displayName,
                        "keywords" to tag.matchingKeywords,
                    )
                },
            )
        },
    ),
)

private fun placeTagSchema(places: List<PlaceTagExtractor.PlaceInput>): Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "places" to mapOf(
            "type" to "array",
            "minItems" to places.size,
            "maxItems" to places.size,
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "placeIndex" to mapOf("type" to "integer", "enum" to places.map { it.placeIndex }),
                    "tags" to mapOf(
                        "type" to "array",
                        "maxItems" to MAX_PLACE_TAG_COUNT,
                        "items" to placeTagItemSchema(places.flatMap { it.candidateTags }.distinct()),
                    ),
                ),
                "required" to listOf("placeIndex", "tags"),
                "additionalProperties" to false,
            ),
        ),
    ),
    "required" to listOf("places"),
    "additionalProperties" to false,
)

private fun placeTagItemSchema(candidateTags: List<PlaceTagDefinition>): Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "tag" to mapOf("type" to "string", "enum" to candidateTags.map { it.tag }),
        "confidence" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1),
        "evidenceSource" to mapOf("type" to "string", "enum" to listOf("BODY", "HASHTAG")),
        "evidenceText" to mapOf("type" to "string"),
    ),
    "required" to listOf("tag", "confidence", "evidenceSource", "evidenceText"),
    "additionalProperties" to false,
)

private const val MAX_PLACE_TAG_COUNT = 4
private const val MAX_IMAGE_COUNT = 20
private const val IMAGE_DETAIL = "high"
