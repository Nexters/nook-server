package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminParsingDecisionStep
import org.every.nook.api.application.admin.AdminParsingEdge
import org.every.nook.api.application.admin.AdminParsingExecution
import org.every.nook.api.application.admin.AdminParsingJobExecution
import org.every.nook.api.application.admin.AdminParsingNode
import org.every.nook.api.application.admin.AdminParsingPipeline
import org.every.nook.api.application.admin.AdminParsingPipelinePort
import org.every.nook.api.application.admin.AdminParsingPosition
import org.every.nook.api.application.admin.AdminParsingRule
import org.every.nook.api.application.admin.AdminParsingRuleSection
import org.every.nook.api.application.admin.AdminPostNotFoundException
import org.every.nook.api.application.admin.AdminProcessingTrace
import org.every.nook.api.application.admin.AdminRuntimeConfiguration
import org.every.nook.api.application.place.HangulOcrRuleSpec
import org.every.nook.api.application.place.PlaceCandidateRuleSpec
import org.every.nook.api.application.place.PlaceParsingRuleSpec
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.instagram.InstagramScrapingProviderMode
import org.every.nook.api.infrastructure.ocr.OcrProviderType
import org.every.nook.api.infrastructure.persistence.config.RuntimeConfigurationJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceIdentityRuleSpec
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.processing.ProcessingTraceJpaRepository
import org.every.nook.api.infrastructure.place.GooglePlacePhotoRuleSpec
import org.every.nook.api.infrastructure.place.PlaceThumbnailProperties
import org.every.nook.api.infrastructure.place.PlaceThumbnailProviderType
import org.every.nook.api.infrastructure.place.toProviderChain
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Clock

@Component
// Operator-facing rule sentences stay intact so the catalog remains searchable and readable as authored.
@Suppress("LargeClass", "LongMethod", "MagicNumber", "MaxLineLength", "TooManyFunctions")
class AdminParsingPipelineAdapter(
    private val configurationRepository: RuntimeConfigurationJpaRepository,
    private val postRepository: PostJpaRepository,
    private val contentJobRepository: PostContentParsingJobJpaRepository,
    private val placeJobRepository: PlaceParsingJobJpaRepository,
    private val traceRepository: ProcessingTraceJpaRepository,
    private val thumbnailProperties: PlaceThumbnailProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : AdminParsingPipelinePort {
    @Transactional(readOnly = true)
    override fun get(postId: Long?): AdminParsingPipeline = AdminParsingPipeline(
        nodes = nodes(),
        edges = edges(),
        configurations = configurations(),
        execution = postId?.let(::execution),
    )

    private fun configurations(): List<AdminRuntimeConfiguration> = listOf(
        instagramConfiguration(),
        ocrConfiguration(),
        thumbnailConfiguration(),
    )

    private fun instagramConfiguration(): AdminRuntimeConfiguration {
        val key = InstagramScrapingProviderMode.CONFIGURATION_KEY
        val configured = configurationRepository.findByConfigurationKey(key)?.configurationValue
        val effective = InstagramScrapingProviderMode.from(configured)
        val valid = configured == null || InstagramScrapingProviderMode.entries.any {
            it.name == configured.trim().uppercase()
        }
        return AdminRuntimeConfiguration(
            key = key,
            configuredValue = configured,
            effectiveValue = effective.name,
            source = if (configured != null && valid) "RUNTIME" else "DEFAULT",
            description = "Instagram 원문 수집 provider와 fallback 방식",
            warnings = if (valid) emptyList() else listOf("알 수 없는 값이어서 기본 provider 모드를 적용합니다."),
        )
    }

    private fun ocrConfiguration(): AdminRuntimeConfiguration {
        val key = OcrProviderType.CONFIGURATION_KEY
        val configured = configurationRepository.findByConfigurationKey(key)?.configurationValue
        val effective = OcrProviderType.parseChain(configured)
        val unknown = configured.unknownValues(OcrProviderType.entries.map { it.name }.toSet())
        val usesDefault = configured == null ||
            (effective == OcrProviderType.DEFAULT_CHAIN && unknown.isNotEmpty())
        return AdminRuntimeConfiguration(
            key = key,
            configuredValue = configured,
            effectiveValue = effective.joinToString(" → "),
            source = if (usesDefault) {
                "DEFAULT"
            } else {
                "RUNTIME"
            },
            description = "커버 및 장소 이미지 OCR provider fallback 순서",
            warnings = unknown.takeIf(List<String>::isNotEmpty)
                ?.let { listOf("알 수 없는 provider를 제외했습니다: ${it.joinToString()}") }
                .orEmpty(),
        )
    }

    private fun thumbnailConfiguration(): AdminRuntimeConfiguration {
        val key = PlaceThumbnailProviderType.CONFIGURATION_KEY
        val configured = configurationRepository.findByConfigurationKey(key)?.configurationValue
        val parsed = PlaceThumbnailProviderType.parse(configured)
        val fallback = thumbnailProperties.provider.toProviderChain()
        val effective = parsed.ifEmpty { fallback }
        val unknown = configured.unknownValues(PlaceThumbnailProviderType.entries.map { it.name }.toSet())
        return AdminRuntimeConfiguration(
            key = key,
            configuredValue = configured,
            effectiveValue = effective.joinToString(" → ").ifEmpty { "DISABLED" },
            source = if (parsed.isNotEmpty()) "RUNTIME" else "ENVIRONMENT_FALLBACK",
            description = "장소 사진과 부가정보를 찾는 provider fallback 순서",
            warnings = buildList {
                if (unknown.isNotEmpty()) add("알 수 없는 provider를 제외했습니다: ${unknown.joinToString()}")
                if (configured != null && parsed.isEmpty()) add("유효한 provider가 없어 환경 기본값을 적용합니다.")
            },
        )
    }

    private fun String?.unknownValues(known: Set<String>): List<String> = orEmpty().split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { it.uppercase() !in known }

    private fun execution(postId: Long): AdminParsingExecution {
        val post = postRepository.findById(postId).orElse(null) ?: throw AdminPostNotFoundException()
        return AdminParsingExecution(
            postId = postId,
            title = post.title,
            content = contentJobRepository.findByPostId(postId)?.toExecution()
                ?: AdminParsingJobExecution("PENDING", null, 0, 0, null, null),
            place = placeJobRepository.findByPostId(postId)?.toExecution(),
            traces = traceRepository.findAllByPostIdOrderByCreatedAtAscIdAsc(postId).map { trace ->
                AdminProcessingTrace(
                    id = requireNotNull(trace.id),
                    flow = trace.flow,
                    stage = trace.stage,
                    action = trace.action,
                    outcome = trace.outcome,
                    attempt = trace.attempt,
                    durationMs = trace.durationMs,
                    details = trace.details?.let { objectMapper.readValue<Map<String, String>>(it) }.orEmpty(),
                    createdAt = trace.createdAt,
                )
            },
        )
    }

    private fun PostContentParsingJobEntity.toExecution() = AdminParsingJobExecution(
        status = status.name,
        stage = progressStage?.name,
        progressPercent = if (status == PostContentParsingStatus.COMPLETED) {
            100
        } else {
            progress().percentAt(clock.instant())
        },
        attemptCount = attemptCount,
        failureReason = failureReason,
        nextAttemptAt = nextAttemptAt.takeIf { status == PostContentParsingStatus.PENDING && attemptCount > 0 },
    )

    private fun PlaceParsingJobEntity.toExecution() = AdminParsingJobExecution(
        status = status.name,
        stage = progressStage?.name,
        progressPercent = if (status == PlaceParsingStatus.COMPLETED) 100 else progress().percentAt(clock.instant()),
        attemptCount = attemptCount,
        failureReason = failureReason,
        nextAttemptAt = nextAttemptAt.takeIf { status == PlaceParsingStatus.PENDING && attemptCount > 0 },
    )

    private fun nodes(): List<AdminParsingNode> = listOf(
        node(
            id = "request",
            title = "게시글 저장 요청",
            subtitle = "파싱 작업 생성",
            lane = "CONTENT",
            x = 0,
            y = 0,
            summary = "URL을 기준으로 콘텐츠 파싱 작업을 만들고 비동기 처리 큐에 전달합니다.",
            inputs = listOf("Instagram 게시글 URL"),
            outputs = listOf("콘텐츠 파싱 작업"),
            decisions = listOf(
                decision(
                    1,
                    "URL 식별",
                    "동일 canonical URL의 게시글이 존재하는가?",
                    null,
                    "기존 게시글 작업 상태를 사용",
                    "새 게시글과 파싱 작업 생성",
                    "SavePostUseCase",
                ),
                decision(
                    2,
                    "비동기 실행",
                    "작업을 처리 가능한 상태로 저장했는가?",
                    null,
                    "콘텐츠 수집 단계로 전달",
                    "요청 실패 반환",
                    "PostContentParsingJobPort",
                ),
            ),
            sections = listOf(section("작업 규칙", rule("재시도", "실패 종류와 시도 횟수에 따라 backoff 후 재실행"))),
        ),
        node(
            id = "content-fetch",
            title = "콘텐츠 조회",
            subtitle = "Instagram 원문 수집",
            lane = "CONTENT",
            x = 280,
            y = 0,
            stages = listOf("CONTENT_FETCH"),
            configurationKeys = listOf(InstagramScrapingProviderMode.CONFIGURATION_KEY),
            summary = "현재 provider 모드에 따라 게시글 본문, 해시태그, 위치 태그와 미디어를 조회합니다.",
            inputs = listOf("canonical URL"),
            outputs = listOf("본문", "해시태그", "위치 태그", "미디어"),
            decisions = listOf(
                decision(
                    1,
                    "Provider 순서 결정",
                    "runtime configuration 값이 유효한가?",
                    "mode = parse(configuredValue) ?: default",
                    "설정된 호출 순서 적용",
                    "기본 provider 모드 적용",
                    "InstagramScrapingProviderMode",
                ),
                decision(
                    2,
                    "수집 결과 채택",
                    "provider가 유효한 게시글 데이터를 반환했는가?",
                    null,
                    "본문·태그·미디어 저장 후보로 전달",
                    "fallback provider 호출 또는 작업 실패",
                    "InstagramPostScrapingProvider",
                ),
            ),
            sections = listOf(
                section(
                    "Provider 선택",
                    rule("BRIGHT_DATA_ONLY", "Bright Data만 호출"),
                    rule("APIFY_ONLY", "Apify만 호출"),
                    rule("BRIGHT_DATA_WITH_APIFY_FALLBACK", "Bright Data 실패 시 Apify"),
                    rule("APIFY_BRIGHT_WITH_DATA_FALLBACK", "Apify 실패 시 Bright Data"),
                ),
            ),
        ),
        node(
            id = "cover-ocr",
            title = "커버 OCR",
            subtitle = "첫 이미지 제목 후보",
            lane = "CONTENT",
            x = 560,
            y = 0,
            stages = listOf("CONTENT_COVER_TITLE"),
            configurationKeys = listOf(OcrProviderType.CONFIGURATION_KEY),
            summary = "첫 번째 이미지의 텍스트를 읽습니다. 실패해도 콘텐츠 파싱은 계속 진행합니다.",
            inputs = listOf("첫 번째 이미지"),
            outputs = listOf("커버 텍스트 후보"),
            decisions = listOf(
                decision(
                    1,
                    "대상 선택",
                    "첫 번째 이미지가 존재하는가?",
                    "media.minByOrNull(sequence)",
                    "OCR provider chain 실행",
                    "텍스트 제목 fallback",
                    "ProcessPostContentParsingJobUseCase",
                ),
                decision(
                    2,
                    "OCR 결과 채택",
                    "OCR 결과를 trim한 문자열이 비어 있지 않은가?",
                    "text.trim().isNotEmpty()",
                    "커버 제목 후보로 저장",
                    "다음 provider 또는 텍스트 제목 사용",
                    "FallbackImageTextExtractor",
                ),
            ),
            sections = listOf(section("Fallback", rule("성공 조건", "비어 있지 않은 첫 OCR 결과"), rule("실패", "텍스트 제목으로 계속 진행"))),
        ),
        node(
            id = "content-inference",
            title = "콘텐츠 추론",
            subtitle = "텍스트 장소 단서 생성",
            lane = "CONTENT",
            x = 840,
            y = 0,
            stages = listOf("CONTENT_INFERENCE"),
            summary = "본문과 해시태그, 위치 태그에서 장소 후보를 찾기 위한 구조화된 단서를 생성합니다.",
            inputs = listOf("본문", "해시태그", "위치 태그"),
            outputs = listOf("장소명", "지역", "주소 힌트", "검색어"),
            decisions = listOf(
                decision(
                    1,
                    "입력 구성",
                    "본문·해시태그·위치 태그 중 하나라도 존재하는가?",
                    null,
                    "구조화 추론 실행",
                    "빈 장소 단서로 계속",
                    "ContentInferenceInput",
                ),
                decision(
                    2,
                    "출력 제한",
                    "추론된 장소/검색어가 허용 개수를 넘는가?",
                    "places.take(${PlaceParsingRuleSpec.MAX_PLACE_COUNT}), queries.take(${PlaceParsingRuleSpec.MAX_QUERY_COUNT})",
                    "상한까지 절단",
                    "전체 결과 유지",
                    "PlaceParsingRuleSpec",
                ),
            ),
            sections = listOf(
                section(
                    "모델 지시 규칙",
                    rule("지역 표현", "도시·구·동·역·공원은 가게가 아니라 검색 지역 단서로 사용"),
                    rule("출력 제한", "장소 단서 최대 ${PlaceParsingRuleSpec.MAX_PLACE_COUNT}개"),
                    rule("검색어", "단서당 최대 ${PlaceParsingRuleSpec.MAX_QUERY_COUNT}개"),
                ),
            ),
        ),
        node(
            id = "content-save",
            title = "콘텐츠 저장",
            subtitle = "장소 파싱 요청",
            lane = "CONTENT",
            x = 1120,
            y = 0,
            stages = listOf("CONTENT_SAVE"),
            summary = "수집한 게시글과 텍스트 장소 단서를 저장하고 장소 파싱 작업을 시작합니다.",
            inputs = listOf("게시글 콘텐츠", "텍스트 장소 단서"),
            outputs = listOf("저장된 게시글", "장소 파싱 작업"),
            decisions = listOf(
                decision(
                    1,
                    "원자적 저장",
                    "콘텐츠와 장소 단서를 저장할 수 있는가?",
                    null,
                    "콘텐츠 완료 및 장소 작업 생성",
                    "트랜잭션 rollback",
                    "PostContentParsingTransactionPort",
                ),
                decision(
                    2,
                    "후속 작업",
                    "저장된 장소 단서 또는 이미지가 존재하는가?",
                    null,
                    "장소 파싱 작업 실행",
                    "장소 없음 결과로 종료 가능",
                    "PlaceParsingJobPort",
                ),
            ),
        ),
        node(
            id = "text-clues",
            title = "텍스트 단서 검증",
            subtitle = "원문 근거 확인",
            lane = "PLACE",
            x = 1120,
            y = 240,
            stages = listOf("PLACE_TEXT_CLUES"),
            summary = "모델이 만든 장소명이나 검색어가 실제 본문 또는 해시태그에 존재하는지 확인합니다.",
            inputs = listOf("텍스트 장소 단서", "본문", "해시태그"),
            outputs = listOf("근거가 확인된 단서"),
            decisions = listOf(
                decision(
                    1,
                    "근거 키 생성",
                    "정규화 키 길이가 ${PlaceCandidateRuleSpec.MIN_GROUNDING_KEY_LENGTH} 이상인가?",
                    "lowercase().filter(isLetterOrDigit)",
                    "본문 포함 여부 검사",
                    "짧고 위험한 근거로 제외",
                    "PlaceClueGrounding",
                ),
                decision(
                    2,
                    "원문 포함",
                    "장소명 또는 검색어 키가 본문·해시태그 키에 포함되는가?",
                    "evidenceKey.contains(clueKey)",
                    "검색 대상 단서로 유지",
                    "텍스트 단서에서 제거",
                    "PlaceClueGrounding",
                ),
            ),
            sections = listOf(
                section(
                    "Grounding",
                    rule("정규화", "소문자 변환 후 문자와 숫자만 유지"),
                    rule("최소 단서 길이", "${PlaceCandidateRuleSpec.MIN_GROUNDING_KEY_LENGTH}자"),
                    rule("통과", "장소명 또는 검색어가 본문·해시태그 정규화 문자열에 포함"),
                ),
            ),
        ),
        node(
            id = "text-resolution",
            title = "장소 후보 판정",
            subtitle = "검색·필터·선택",
            lane = "PLACE",
            x = 1400,
            y = 240,
            stages = listOf("PLACE_TEXT_RESOLUTION"),
            summary = "검색 결과를 주소와 이름 근거로 좁히고, 하나로 확정되지 않을 때만 모델 선택을 사용합니다.",
            inputs = listOf("장소 단서", "Kakao·Naver 후보"),
            outputs = listOf("확정 장소", "해결하지 못한 단서"),
            decisions = candidateDecisions(),
            sections = candidateSections(),
            examples = listOf(
                "‘카페 노티드 청담’ → ‘카페노티드청담’으로 정규화한 뒤 후보 이름과 주소 충돌 여부를 검사합니다.",
                "strict 후보가 정확히 1개면 모델을 호출하지 않고 확정합니다. 0개 또는 여러 개면 grounded 후보를 확인합니다.",
                "모델이 후보를 골라도 원문·주소 근거 재검증에 실패하면 해당 단서는 미해결로 남깁니다.",
            ),
        ),
        node(
            id = "image-decision",
            title = "이미지 분석 판단",
            subtitle = "조건부 분기",
            lane = "PLACE",
            kind = "DECISION",
            x = 1680,
            y = 240,
            summary = "텍스트만으로 장소를 충분히 찾았는지 판단해 이미지 OCR 비용을 제어합니다.",
            inputs = listOf("텍스트 단서 수", "해결 장소 수", "예상 장소 수", "이미지 수"),
            outputs = listOf("이미지 분석", "제목 확정으로 건너뛰기"),
            decisions = listOf(
                decision(
                    1,
                    "이미지 존재",
                    "이미지가 한 장 이상 존재하는가?",
                    "images.isNotEmpty()",
                    "텍스트 충족도 검사",
                    "이미지 분석 생략",
                    "requiresImageAnalysis",
                ),
                decision(
                    2,
                    "텍스트 결과 없음",
                    "텍스트 단서가 없거나 해결된 장소가 0개인가?",
                    "textClueCount == 0 || textResolvedCount == 0",
                    "이미지 OCR 실행",
                    "예상 장소 수 검사",
                    "requiresImageAnalysis",
                ),
                decision(
                    3,
                    "예상 개수 부족",
                    "본문의 예상 장소 수가 있고 텍스트 단서 수가 더 적은가?",
                    "textClueCount < expectedPlaceCount",
                    "이미지 OCR 실행",
                    "제목 확정으로 건너뛰기",
                    "requiresImageAnalysis",
                ),
            ),
            sections = listOf(
                section(
                    "이미지 분석 실행",
                    rule("텍스트 단서 없음", "실행"),
                    rule("텍스트 해결 장소 없음", "실행"),
                    rule("예상 장소 수보다 텍스트 단서 부족", "실행"),
                    rule("이미지 없음", "항상 생략"),
                ),
                section(
                    "예상 장소 수",
                    rule("본문 패턴", "2~${PlaceParsingRuleSpec.MAX_EXPECTED_PLACE_COUNT} 사이의 ‘N곳·N선·N군데’ 중 최댓값"),
                ),
            ),
            examples = listOf(
                "자모 분해 뒤 길이가 6이고 편집 거리가 2라면 2 ≤ 2, 2×3 ≤ 6을 모두 만족해 통과합니다.",
                "편집 거리가 2라도 자모 길이가 5라면 2×3 ≤ 5가 거짓이므로 유사 이름으로 인정하지 않습니다.",
            ),
        ),
        node(
            id = "image-ocr",
            title = "이미지 OCR",
            subtitle = "장소 카드 텍스트 추출",
            lane = "PLACE",
            x = 1960,
            y = 360,
            stages = listOf("PLACE_IMAGE_OCR"),
            configurationKeys = listOf(OcrProviderType.CONFIGURATION_KEY),
            summary = "최대 이미지 수 안에서 캐시되지 않은 이미지만 OCR하고 provider 실패·빈 결과 시 다음 provider로 이동합니다.",
            inputs = listOf("최대 ${PlaceParsingRuleSpec.MAX_IMAGE_COUNT}개 이미지", "기존 OCR 캐시"),
            outputs = listOf("이미지별 텍스트"),
            decisions = listOf(
                decision(
                    1,
                    "처리 범위",
                    "이미지 순서가 ${PlaceParsingRuleSpec.MAX_IMAGE_COUNT}개 이내인가?",
                    "images.sortedBy(sequence).take(${PlaceParsingRuleSpec.MAX_IMAGE_COUNT})",
                    "캐시 검사",
                    "이번 실행에서 제외",
                    "PlaceParsingRuleSpec",
                ),
                decision(
                    2,
                    "캐시 재사용",
                    "저장된 OCR 텍스트가 비어 있지 않은가?",
                    "cachedText.isNotBlank()",
                    "provider 호출 없이 재사용",
                    "provider chain 실행",
                    "PlaceImageOcrCache",
                ),
                decision(
                    3,
                    "Provider fallback",
                    "현재 provider 결과가 성공이며 비어 있지 않은가?",
                    "result.isSuccess && text.isNotBlank()",
                    "해당 결과 채택 후 종료",
                    "다음 provider 호출",
                    "FallbackImageTextExtractor",
                ),
                decision(
                    4,
                    "한글 근접 판정",
                    "자모 편집 거리와 오류 비율을 모두 만족하는가?",
                    "distance <= ${HangulOcrRuleSpec.MAX_EDIT_DISTANCE} && distance * ${HangulOcrRuleSpec.MAX_ERROR_RATIO_DENOMINATOR} <= maxLength",
                    "OCR 유사 근거 인정",
                    "유사 근거로 사용하지 않음",
                    "HangulOcrMatcher",
                ),
            ),
            sections = listOf(
                section("처리", rule("캐시", "텍스트가 있는 이미지 OCR 결과는 재사용"), rule("동시성", "기본 4개 이미지")),
                section(
                    "한글 음절 유사도",
                    rule("분해", "완성형 음절을 초성·중성·종성으로 분해"),
                    rule("최대 편집 거리", HangulOcrRuleSpec.MAX_EDIT_DISTANCE.toString()),
                    rule("최대 오류 비율", "1/${HangulOcrRuleSpec.MAX_ERROR_RATIO_DENOMINATOR}"),
                ),
            ),
        ),
        node(
            id = "image-clues",
            title = "이미지 단서 복원",
            subtitle = "카드·프로필 근거 결합",
            lane = "PLACE",
            x = 2240,
            y = 360,
            stages = listOf("PLACE_IMAGE_CLUES"),
            summary = "OCR 텍스트에서 장소 카드를 인식하고 주소, 이미지 순서와 프로필 힌트를 장소 단서에 결합합니다.",
            inputs = listOf("이미지 OCR 텍스트"),
            outputs = listOf("이미지 근거가 연결된 장소 단서"),
            decisions = listOf(
                decision(
                    1,
                    "주소 카드 탐지",
                    "OCR 텍스트에 도로명·지번·층·호수 패턴이 있는가?",
                    null,
                    "주소 단위 카드로 분리",
                    "일반 OCR 단서로 유지",
                    "PlaceImageClueExtractor",
                ),
                decision(
                    2,
                    "예상 개수 보정",
                    "PICK N과 주소 카드 수 중 어느 값이 큰가?",
                    "max(pickCount, addressCardCount)",
                    "큰 값을 이미지 예상 장소 수로 사용",
                    "알 수 없음 유지",
                    "effectiveExpectedPlaceCount",
                ),
                decision(
                    3,
                    "대표 미디어",
                    "한 이미지가 정확히 한 장소 단서에만 연결되는가?",
                    "evidencePlaceCount == 1",
                    "해당 이미지를 대표 미디어 후보로 허용",
                    "공유 이미지로 취급",
                    "PlaceClueEvidence",
                ),
            ),
            sections = listOf(
                section(
                    "카드 복원",
                    rule("주소", "도로명·지번 주소 패턴과 층·호수 인식"),
                    rule("장소 수", "주소 카드 수와 ‘PICK N’ 중 큰 값"),
                    rule("단독 이미지 근거", "하나의 이미지가 하나의 장소에만 근거로 연결된 경우 미디어 대표 사용 허용"),
                ),
            ),
        ),
        node(
            id = "image-resolution",
            title = "이미지 장소 판정",
            subtitle = "OCR 오차 허용 매칭",
            lane = "PLACE",
            x = 2520,
            y = 360,
            stages = listOf("PLACE_IMAGE_RESOLUTION"),
            summary = "텍스트 후보 판정과 같은 안전 규칙에 OCR 오차 허용 조건을 추가해 장소를 확정합니다.",
            inputs = listOf("이미지 장소 단서", "검색 후보"),
            outputs = listOf("확정 장소", "미해결 단서"),
            decisions = candidateDecisions(includeOcr = true),
            sections = candidateSections(),
            examples = listOf(
                "OCR 이름 키가 3자 이상이면 후보 이름과 Levenshtein 거리를 계산하고 3 이하일 때 복구 근거로 사용할 수 있습니다.",
                "이름이 비슷해도 명시된 구·군·시 또는 층·호수가 충돌하면 후보에서 제거합니다.",
            ),
        ),
        node(
            id = "title-finalization",
            title = "게시글 제목 확정",
            subtitle = "근거 우선순위 적용",
            lane = "PLACE",
            x = 2800,
            y = 240,
            stages = listOf("TITLE_FINALIZATION"),
            summary = "관리자 수정 여부를 보존하면서 본문·커버·확정 장소 근거를 이용해 최종 제목을 선택합니다.",
            inputs = listOf("본문", "커버 OCR", "확정 장소", "이미지 OCR"),
            outputs = listOf("최종 게시글 제목"),
            decisions = listOf(
                decision(
                    1,
                    "관리자 수정 보존",
                    "제목이 관리자에 의해 수정되었는가?",
                    "manuallyOverridden == true",
                    "기존 제목 유지 후 종료",
                    "자동 후보 평가",
                    "finalizePostTitle",
                ),
                decision(
                    2,
                    "장소 기반 제목",
                    "확정 장소와 선언된 장소 수가 제목 근거를 충족하는가?",
                    null,
                    "장소 목록 기반 제목 생성",
                    "본문·커버 후보 평가",
                    "PostTitleFinalizer",
                ),
                decision(
                    3,
                    "Fallback",
                    "본문 제목 후보가 비어 있지 않은가?",
                    null,
                    "본문 후보 채택",
                    "커버 OCR 또는 기존 제목 유지",
                    "PostTitleFinalizer",
                ),
            ),
            sections = listOf(
                section(
                    "안전 규칙",
                    rule("관리자 수정", "기존 관리자 교정 제목을 덮어쓰지 않음"),
                    rule("장소 없음", "본문 또는 커버 기반 fallback 제목 사용"),
                ),
            ),
        ),
        node(
            id = "place-save",
            title = "장소 저장·병합",
            subtitle = "공용 장소 식별",
            lane = "PLACE",
            x = 3080,
            y = 240,
            stages = listOf("PLACE_SAVE"),
            summary = "동일 provider ID를 우선 사용하고, provider가 달라도 이름·주소·거리 규칙으로 기존 공용 장소와 병합합니다.",
            inputs = listOf("확정 장소 후보"),
            outputs = listOf("게시글-장소 연결", "공용 장소"),
            decisions = listOf(
                decision(
                    1,
                    "Provider ID",
                    "provider와 externalPlaceId가 모두 같은 장소가 존재하는가?",
                    null,
                    "동일 공용 장소로 즉시 병합",
                    "교차 provider 판정",
                    "PlaceIdentityMatcher",
                ),
                decision(
                    2,
                    "일반 동일성",
                    "이름 포함·주소 일치·거리 ${PlaceIdentityRuleSpec.MAX_DISTANCE_METERS.toInt()}m 이하인가?",
                    "namesMatch && addressesMatch && distance <= ${PlaceIdentityRuleSpec.MAX_DISTANCE_METERS.toInt()}",
                    "기존 장소에 병합",
                    "인접 주소 판정",
                    "PlaceIdentityMatcher",
                ),
                decision(
                    3,
                    "인접 주소 복구",
                    "이름 완전 일치·건물번호 차이 ≤ ${PlaceIdentityRuleSpec.MAX_BUILDING_NUMBER_DIFFERENCE}·거리 ≤ ${PlaceIdentityRuleSpec.ADJACENT_ADDRESS_MAX_DISTANCE_METERS.toInt()}m인가?",
                    null,
                    "기존 장소에 병합",
                    "새 공용 장소 생성",
                    "PlaceIdentityMatcher",
                ),
            ),
            sections = listOf(
                section(
                    "동일 장소 판정",
                    rule("일반", "이름 포함·주소 일치·거리 ${PlaceIdentityRuleSpec.MAX_DISTANCE_METERS.toInt()}m 이하"),
                    rule(
                        "교차 provider",
                        "이름 완전 일치·건물번호 차이 " +
                            "${PlaceIdentityRuleSpec.MAX_BUILDING_NUMBER_DIFFERENCE} 이하·거리 " +
                            "${PlaceIdentityRuleSpec.ADJACENT_ADDRESS_MAX_DISTANCE_METERS.toInt()}m 이하",
                    ),
                    rule("이름 키 최소 길이", "${PlaceIdentityRuleSpec.MIN_NAME_KEY_LENGTH}자"),
                ),
            ),
        ),
        node(
            id = "thumbnail",
            title = "장소 사진 보강",
            subtitle = "비동기 provider fallback",
            lane = "MEDIA",
            x = 3360,
            y = 480,
            configurationKeys = listOf(PlaceThumbnailProviderType.CONFIGURATION_KEY),
            summary = "저장된 장소를 provider 순서대로 조회하고 사진을 얻은 첫 provider에서 종료합니다.",
            inputs = listOf("확정 장소", "게시글 미디어"),
            outputs = listOf("썸네일", "사진", "영업시간", "Google Place ID"),
            decisions = listOf(
                decision(
                    1,
                    "Provider 순회",
                    "현재 provider가 DISABLED가 아닌가?",
                    null,
                    "장소 검색 실행",
                    "사진 보강 종료",
                    "PlaceThumbnailProviderChain",
                ),
                decision(
                    2,
                    "Google 후보 선택",
                    "최고 후보 점수가 최소 ${GooglePlacePhotoRuleSpec.MIN_MATCH_SCORE}점 이상인가?",
                    "max(score) >= ${GooglePlacePhotoRuleSpec.MIN_MATCH_SCORE}",
                    "후보 사진 조회",
                    "다음 provider로 이동",
                    "GooglePlacePhotoProvider",
                ),
                decision(
                    3,
                    "결과 채택",
                    "반환된 사진 목록이 비어 있지 않은가?",
                    "photos.isNotEmpty()",
                    "사진과 부가정보 저장 후 종료",
                    "다음 provider로 이동",
                    "PlaceThumbnailProviderChain",
                ),
            ),
            sections = googlePhotoSections(),
            examples = listOf(
                "이름 완전 일치 +50, 주소 일치 +30, 100m 이내 +25, 사진 있음 +10이면 총 115점으로 선택 기준을 통과합니다.",
                "최고 점수가 ${GooglePlacePhotoRuleSpec.MIN_MATCH_SCORE}점 미만이면 Google 결과를 버리고 다음 provider로 이동합니다.",
            ),
        ),
    )

    private fun candidateSections(): List<AdminParsingRuleSection> = listOf(
        section(
            "이름 정규화·유사도",
            rule("정규화", "소문자 변환 후 문자와 숫자만 유지"),
            rule("포함 일치", "양쪽 이름 키가 ${PlaceCandidateRuleSpec.MIN_NAME_COMPATIBILITY_KEY_LENGTH}자 이상"),
            rule(
                "동일 길이 fuzzy",
                "${PlaceCandidateRuleSpec.MIN_FUZZY_NAME_LENGTH}자 이상, " +
                    "글자 차이 ${PlaceCandidateRuleSpec.MAX_NAME_CHARACTER_DIFFERENCE}개 이하",
            ),
            rule(
                "OCR 편집 거리",
                "${PlaceCandidateRuleSpec.MIN_NEAR_OCR_NAME_LENGTH}자 이상, " +
                    "최대 ${PlaceCandidateRuleSpec.MAX_OCR_NAME_EDIT_DISTANCE}",
            ),
        ),
        section(
            "주소·근거",
            rule("주소 키", "도로명/지번과 건물번호가 일치해야 함"),
            rule("도로명 OCR", "구·군·시가 충돌하지 않고 도로명 편집 거리 1 이하"),
            rule("층·호수", "힌트에 명시된 층·호수는 후보 주소와 같은 값이어야 함"),
            rule("모델 선택", "strict/grounded 후보가 하나가 아닐 때만 사용하고, 선택 후 근거를 다시 검증"),
        ),
    )

    private fun candidateDecisions(includeOcr: Boolean = false): List<AdminParsingDecisionStep> = buildList {
        add(
            decision(
                1,
                "검색 후보 호환성",
                "이름 근거가 호환되고 주소·행정구역·층·호수의 명시적 충돌이 없는가?",
                "compatibleName && !locationConflict",
                "후보군에 유지",
                "후보에서 제거",
                "PlaceClueCandidateMatcher.compatibleWith",
            ),
        )
        if (includeOcr) {
            add(
                decision(
                    2,
                    "OCR 이름 복구",
                    "이름 키가 ${PlaceCandidateRuleSpec.MIN_NEAR_OCR_NAME_LENGTH}자 이상이고 편집 거리 ≤ ${PlaceCandidateRuleSpec.MAX_OCR_NAME_EDIT_DISTANCE}인가?",
                    "levenshtein(clueName, candidateName) <= ${PlaceCandidateRuleSpec.MAX_OCR_NAME_EDIT_DISTANCE}",
                    "이름 근거로 인정",
                    "주소·검색 근거만으로 계속 평가",
                    "PlaceClue.hasPlausibleOcrIdentity",
                ),
            )
        }
        val baseOrder = if (includeOcr) 3 else 2
        add(
            decision(
                baseOrder,
                "Strict/Grounded 확정",
                "strict 후보가 1개이거나 grounded 후보가 1개로 유일한가?",
                "unique(strictMatches) ?: unique(groundedMatches)",
                "해당 장소 즉시 확정",
                "모델 선택 단계로 이동",
                "ProcessPlaceParsingJobUseCase.resolve",
            ),
        )
        add(
            decision(
                baseOrder + 1,
                "모델 선택 재검증",
                "모델 선택 후보가 원문·주소·검색 근거를 다시 만족하는가?",
                "selected in compatibleCandidates && clue.isSupportedBy(selected)",
                "최종 장소로 확정",
                "미해결 단서로 기록",
                "ProcessPlaceParsingJobUseCase.resolve",
            ),
        )
    }

    private fun googlePhotoSections(): List<AdminParsingRuleSection> = listOf(
        section(
            "Google 후보 점수",
            rule("최소 선택 점수", GooglePlacePhotoRuleSpec.MIN_MATCH_SCORE.toString()),
            rule("이름 완전 일치", "+${GooglePlacePhotoRuleSpec.EXACT_NAME_SCORE}"),
            rule("이름 포함", "+${GooglePlacePhotoRuleSpec.CONTAINS_NAME_SCORE}"),
            rule("이름 토큰 일치", "+${GooglePlacePhotoRuleSpec.TOKEN_NAME_SCORE}"),
            rule("카테고리 일치", "+${GooglePlacePhotoRuleSpec.CATEGORY_MATCH_SCORE}"),
            rule("주소 일치 / 도시 일치", "+30 / +10"),
            rule(
                "거리",
                "≤${GooglePlacePhotoRuleSpec.CLOSE_MATCH_DISTANCE_METERS.toInt()}m +25 · " +
                    "≤${GooglePlacePhotoRuleSpec.MAX_MATCH_DISTANCE_METERS.toInt()}m +15 · " +
                    "≤${GooglePlacePhotoRuleSpec.FAR_MATCH_DISTANCE_METERS.toInt()}m +5",
            ),
            rule(
                "사진 있음 / 없음",
                "+${GooglePlacePhotoRuleSpec.PHOTO_BONUS_SCORE} / " +
                    GooglePlacePhotoRuleSpec.NO_PHOTO_PENALTY,
            ),
            rule(
                "지역 불일치",
                ">${GooglePlacePhotoRuleSpec.REGION_MISMATCH_DISTANCE_METERS.toInt()}m이고 " +
                    "도시 불일치 시 -${GooglePlacePhotoRuleSpec.REGION_MISMATCH_PENALTY}",
            ),
        ),
        section(
            "Fallback",
            rule("다음 provider", "호출 실패 또는 사진이 비어 있을 때"),
            rule("종료", "사진을 얻은 첫 provider에서 종료하고 앞 provider의 부가정보는 병합"),
        ),
    )

    private fun edges(): List<AdminParsingEdge> = listOf(
        edge("request", "content-fetch"),
        edge("content-fetch", "cover-ocr"),
        edge("cover-ocr", "content-inference"),
        edge("content-inference", "content-save"),
        edge("content-save", "text-clues"),
        edge("text-clues", "text-resolution"),
        edge("text-resolution", "image-decision"),
        edge("image-decision", "image-ocr", "필요", "conditional"),
        edge("image-ocr", "image-clues"),
        edge("image-clues", "image-resolution"),
        edge("image-resolution", "title-finalization"),
        edge("image-decision", "title-finalization", "충분", "skip"),
        edge("title-finalization", "place-save"),
        edge("place-save", "thumbnail", "비동기", "async"),
    )

    private fun node(
        id: String,
        title: String,
        subtitle: String,
        lane: String,
        x: Int,
        y: Int,
        summary: String,
        inputs: List<String>,
        outputs: List<String>,
        kind: String = "PROCESS",
        stages: List<String> = emptyList(),
        configurationKeys: List<String> = emptyList(),
        decisions: List<AdminParsingDecisionStep> = emptyList(),
        sections: List<AdminParsingRuleSection> = emptyList(),
        examples: List<String> = emptyList(),
    ) = AdminParsingNode(
        id,
        title,
        subtitle,
        lane,
        kind,
        AdminParsingPosition(x, y),
        summary,
        inputs,
        outputs,
        stages,
        configurationKeys,
        decisions,
        sections,
        examples,
    )

    private fun section(title: String, vararg rules: AdminParsingRule): AdminParsingRuleSection =
        AdminParsingRuleSection(title, rules = rules.toList())

    private fun rule(label: String, value: String, description: String? = null) =
        AdminParsingRule(label, value, description)

    @Suppress("LongParameterList")
    private fun decision(
        order: Int,
        title: String,
        condition: String,
        expression: String?,
        onPass: String,
        onFail: String,
        source: String,
    ) = AdminParsingDecisionStep(order, title, condition, expression, onPass, onFail, source)

    private fun edge(source: String, target: String, label: String? = null, kind: String = "default") =
        AdminParsingEdge("$source-$target", source, target, label, kind)
}
