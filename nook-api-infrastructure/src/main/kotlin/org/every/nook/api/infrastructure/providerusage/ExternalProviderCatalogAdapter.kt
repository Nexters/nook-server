package org.every.nook.api.infrastructure.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderCatalogEntry
import org.every.nook.api.application.providerusage.ExternalProviderCatalogPort
import org.every.nook.api.infrastructure.clova.ClovaOcrProperties
import org.every.nook.api.infrastructure.corepin.CorepinOcrProperties
import org.every.nook.api.infrastructure.instagram.ApifyProperties
import org.every.nook.api.infrastructure.instagram.BrightDataProperties
import org.every.nook.api.infrastructure.instagram.InstagramScrapingProviderMode
import org.every.nook.api.infrastructure.ocr.OcrProviderType
import org.every.nook.api.infrastructure.openai.OpenAiProperties
import org.every.nook.api.infrastructure.persistence.config.RuntimeConfigurationJpaRepository
import org.every.nook.api.infrastructure.place.ApifyGoogleMapsProperties
import org.every.nook.api.infrastructure.place.ApifyNaverPlacePhotoProperties
import org.every.nook.api.infrastructure.place.KakaoPlaceProperties
import org.every.nook.api.infrastructure.place.NaverPlaceProperties
import org.every.nook.api.infrastructure.place.PlaceThumbnailProperties
import org.every.nook.api.infrastructure.place.PlaceThumbnailProviderType
import org.every.nook.api.infrastructure.place.toProviderChain
import org.springframework.stereotype.Component

@Component
@Suppress("LongParameterList", "TooManyFunctions")
class ExternalProviderCatalogAdapter(
    private val configurations: RuntimeConfigurationJpaRepository,
    private val brightData: BrightDataProperties,
    private val apify: ApifyProperties,
    private val naver: NaverPlaceProperties,
    private val kakao: KakaoPlaceProperties,
    private val apifyGoogle: ApifyGoogleMapsProperties,
    private val apifyNaver: ApifyNaverPlacePhotoProperties,
    private val corepin: CorepinOcrProperties,
    private val clova: ClovaOcrProperties,
    private val openAi: OpenAiProperties,
    private val thumbnail: PlaceThumbnailProperties,
) : ExternalProviderCatalogPort {
    override fun get(): List<ExternalProviderCatalogEntry> = buildList {
        addAll(instagramEntries())
        addAll(placeSearchEntries())
        addAll(thumbnailEntries())
        addAll(ocrEntries())
        add(openAiEntry())
    }

    private fun instagramEntries(): List<ExternalProviderCatalogEntry> {
        val mode = InstagramScrapingProviderMode.from(value(InstagramScrapingProviderMode.CONFIGURATION_KEY))
        val brightState = when (mode) {
            InstagramScrapingProviderMode.BRIGHT_DATA_ONLY,
            InstagramScrapingProviderMode.BRIGHT_DATA_WITH_APIFY_FALLBACK,
            -> ACTIVE

            InstagramScrapingProviderMode.APIFY_BRIGHT_WITH_DATA_FALLBACK -> FALLBACK

            InstagramScrapingProviderMode.APIFY_ONLY -> DISABLED
        }
        val apifyState = when (mode) {
            InstagramScrapingProviderMode.APIFY_ONLY,
            InstagramScrapingProviderMode.APIFY_BRIGHT_WITH_DATA_FALLBACK,
            -> ACTIVE

            InstagramScrapingProviderMode.BRIGHT_DATA_WITH_APIFY_FALLBACK -> FALLBACK

            InstagramScrapingProviderMode.BRIGHT_DATA_ONLY -> DISABLED
        }
        return listOf(
            entry(
                provider = "BRIGHT_DATA",
                name = "Bright Data Instagram Scraper",
                category = "콘텐츠 수집",
                purpose = "Instagram 게시물·릴스 원문 수집",
                configured = brightData.apiToken.isNotBlank(),
                state = brightState,
                reason = "현재 Instagram 모드: ${mode.name}",
                policy = "실패 또는 timeout 시 설정에 따라 Apify로 fallback",
            ),
            entry(
                provider = "APIFY",
                name = "Apify Instagram Scraper",
                category = "콘텐츠 수집",
                purpose = "Instagram 게시물 원문 수집",
                configured = apify.apiToken.isNotBlank() && apify.actorId.isNotBlank(),
                state = apifyState,
                reason = "현재 Instagram 모드: ${mode.name}",
                policy = "실패 또는 timeout 시 설정에 따라 Bright Data로 fallback",
            ),
        )
    }

    private fun placeSearchEntries(): List<ExternalProviderCatalogEntry> = listOf(
        entry(
            "NAVER_LOCAL",
            "Naver Local Search",
            "장소 검색",
            "장소 후보 우선 검색",
            naver.clientId.isNotBlank() && naver.clientSecret.isNotBlank(),
            ACTIVE,
            "장소 검색의 우선 Provider",
            "Naver 최고 점수가 80점 미만이거나 호출 실패 시 Kakao 호출",
        ),
        entry(
            "KAKAO_LOCAL",
            "Kakao Local Search",
            "장소 검색",
            "Naver 결과 보완·검증 검색",
            kakao.restApiKey.isNotBlank(),
            FALLBACK,
            "Naver 결과 신뢰도가 부족할 때 조건부 호출",
            "Naver 최고 점수가 80점 이상이면 호출하지 않음",
        ),
    )

    private fun thumbnailEntries(): List<ExternalProviderCatalogEntry> {
        val configured = value(PlaceThumbnailProviderType.CONFIGURATION_KEY)
        val selected = PlaceThumbnailProviderType.parse(configured).ifEmpty { thumbnail.provider.toProviderChain() }
            .takeWhile { it != PlaceThumbnailProviderType.DISABLED }
        return listOf(
            thumbnailEntry(
                "APIFY_GOOGLE_MAPS",
                "Apify Google Maps Scraper",
                "장소 사진·부가정보",
                "Google Maps 장소 사진과 영업정보 수집",
                apifyGoogle.apiToken.isNotBlank() && apifyGoogle.actorId.isNotBlank(),
                PlaceThumbnailProviderType.APIFY_GOOGLE,
                selected,
            ),
            thumbnailEntry(
                "APIFY_NAVER_PLACE",
                "Apify Naver Place Scrapers",
                "장소 사진·부가정보",
                "Naver Map 검색 후 Place 사진 수집",
                apifyNaver.apiToken.isNotBlank() &&
                    apifyNaver.searchActorId.isNotBlank() && apifyNaver.photoActorId.isNotBlank(),
                PlaceThumbnailProviderType.APIFY_NAVER_PLACE,
                selected,
            ),
        )
    }

    private fun thumbnailEntry(
        provider: String,
        name: String,
        category: String,
        purpose: String,
        configured: Boolean,
        type: PlaceThumbnailProviderType,
        selected: List<PlaceThumbnailProviderType>,
    ): ExternalProviderCatalogEntry {
        val index = selected.indexOf(type)
        val state = when (index) {
            0 -> ACTIVE
            -1 -> DISABLED
            else -> FALLBACK
        }
        val chain = selected.joinToString(" → ").ifEmpty { "DISABLED" }
        return entry(
            provider,
            name,
            category,
            purpose,
            configured,
            state,
            "현재 장소 사진 chain: $chain",
            "앞선 Provider가 사진을 반환하지 않거나 실패하면 다음 Provider 호출",
        )
    }

    private fun ocrEntries(): List<ExternalProviderCatalogEntry> {
        val selected = OcrProviderType.parseChain(value(OcrProviderType.CONFIGURATION_KEY))
        return listOf(
            ocrEntry("COREPIN", "Corepin OCR", corepin.apiKey.isNotBlank(), OcrProviderType.COREPIN, selected),
            ocrEntry(
                "CLOVA_OCR",
                "Naver CLOVA OCR",
                clova.invokeUrl.isNotBlank() && clova.secretKey.isNotBlank(),
                OcrProviderType.CLOVA,
                selected,
            ),
        )
    }

    private fun ocrEntry(
        provider: String,
        name: String,
        configured: Boolean,
        type: OcrProviderType,
        selected: List<OcrProviderType>,
    ): ExternalProviderCatalogEntry {
        val index = selected.indexOf(type)
        val state = when (index) {
            0 -> ACTIVE
            -1 -> DISABLED
            else -> FALLBACK
        }
        return entry(
            provider,
            name,
            "OCR·추론",
            "이미지 내 장소 텍스트 추출",
            configured,
            state,
            "현재 OCR chain: ${selected.joinToString(" → ")}",
            "모든 이미지에서 텍스트를 얻지 못하거나 실패하면 다음 OCR Provider 호출",
        )
    }

    private fun openAiEntry(): ExternalProviderCatalogEntry {
        val ocr = OcrProviderType.parseChain(value(OcrProviderType.CONFIGURATION_KEY))
        val ocrPosition = ocr.indexOf(OcrProviderType.OPENAI)
        val role = when (ocrPosition) {
            0 -> "OCR 주 Provider이며 콘텐츠·장소 추론에도 사용"
            -1 -> "OCR에서는 제외되었지만 콘텐츠·장소 추론에 사용"
            else -> "OCR fallback이며 콘텐츠·장소 추론에도 사용"
        }
        return entry(
            "OPENAI",
            "OpenAI",
            "OCR·추론",
            "장소 단서·제목 추론 및 이미지 텍스트 추출",
            openAi.apiKey.isNotBlank(),
            ACTIVE,
            role,
            "동시 요청 제한과 1s/2s/4s rate-limit 재시도 적용",
        )
    }

    private fun entry(
        provider: String,
        name: String,
        category: String,
        purpose: String,
        configured: Boolean,
        state: String,
        reason: String,
        policy: String,
    ) = ExternalProviderCatalogEntry(
        provider = provider,
        displayName = name,
        category = category,
        purpose = purpose,
        runtimes = listOf("API", "WORKER"),
        credentialConfigured = configured,
        operationalState = if (configured) state else MISCONFIGURED,
        stateReason = if (configured) reason else "필수 credential이 설정되지 않음",
        policy = policy,
    )

    private fun value(key: String): String? = configurations.findByConfigurationKey(key)?.configurationValue

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val FALLBACK = "FALLBACK"
        const val DISABLED = "DISABLED"
        const val MISCONFIGURED = "MISCONFIGURED"
    }
}
