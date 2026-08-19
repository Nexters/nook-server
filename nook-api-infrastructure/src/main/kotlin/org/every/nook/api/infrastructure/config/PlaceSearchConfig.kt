package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.GetPlaceDetailUseCase
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.application.place.UpdatePlaceMemoUseCase
import org.every.nook.api.application.place.port.PlaceDetailQueryPort
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort
import org.every.nook.api.application.place.port.UpdatePlaceMemoPort
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.every.nook.api.infrastructure.place.ApifyNaverPlaceProperties
import org.every.nook.api.infrastructure.place.ApifyNaverPlaceSearchProvider
import org.every.nook.api.infrastructure.place.KakaoPlaceMapper
import org.every.nook.api.infrastructure.place.KakaoPlaceProperties
import org.every.nook.api.infrastructure.place.KakaoPlaceSearchProvider
import org.every.nook.api.infrastructure.place.NaverPlaceMapper
import org.every.nook.api.infrastructure.place.NaverPlaceProperties
import org.every.nook.api.infrastructure.place.NaverPlaceSearchProvider
import org.every.nook.api.infrastructure.place.PlaceParsingProviderType
import org.every.nook.api.infrastructure.place.PrioritizedPlaceSearchProvider
import org.every.nook.api.infrastructure.place.RuntimePlaceSearchProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
@EnableConfigurationProperties(KakaoPlaceProperties::class, NaverPlaceProperties::class)
class PlaceSearchConfig {
    @Bean
    fun kakaoPlaceMapper(): KakaoPlaceMapper = KakaoPlaceMapper()

    @Bean
    fun naverPlaceMapper(): NaverPlaceMapper = NaverPlaceMapper()

    @Bean("kakaoPlaceRestClient")
    fun kakaoPlaceRestClient(properties: KakaoPlaceProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean("naverPlaceRestClient")
    fun naverPlaceRestClient(properties: NaverPlaceProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean("kakaoPlaceSearchProvider")
    fun kakaoPlaceSearchProvider(
        @Qualifier("kakaoPlaceRestClient") restClient: RestClient,
        properties: KakaoPlaceProperties,
        mapper: KakaoPlaceMapper,
    ): KakaoPlaceSearchProvider = KakaoPlaceSearchProvider(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        mapper = mapper,
    )

    @Bean("naverPlaceSearchProvider")
    fun naverPlaceSearchProvider(
        @Qualifier("naverPlaceRestClient") restClient: RestClient,
        properties: NaverPlaceProperties,
        mapper: NaverPlaceMapper,
    ): PlaceSearchProvider = NaverPlaceSearchProvider(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        mapper = mapper,
    )

    @Bean
    @Primary
    fun placeSearchProvider(
        @Qualifier("kakaoPlaceSearchProvider") kakaoProvider: PlaceSearchProvider,
        @Qualifier("naverPlaceSearchProvider") naverProvider: PlaceSearchProvider,
        @Qualifier("apifyNaverPlaceRestClient") apifyRestClient: RestClient,
        apifyProperties: ApifyNaverPlaceProperties,
        responseCache: ObjectProvider<ScrapingProviderResponseCache>,
        configurationReader: RuntimeConfigurationReader,
    ): PlaceSearchProvider {
        val legacy = PrioritizedPlaceSearchProvider(kakaoProvider, naverProvider)
        val apify = ApifyNaverPlaceSearchProvider(
            restClient = apifyRestClient,
            objectMapper = jacksonObjectMapper(),
            properties = apifyProperties,
            responseCache = responseCache.ifAvailable,
        )
        return RuntimePlaceSearchProvider(
            providers = mapOf(
                PlaceParsingProviderType.APIFY_NAVER to apify,
                PlaceParsingProviderType.LEGACY to legacy,
            ),
            configurationReader = configurationReader,
        )
    }

    @Bean
    fun searchPlaceCandidatesUseCase(
        @Qualifier("placeSearchProvider") provider: PlaceSearchProvider,
    ): SearchPlaceCandidatesUseCase = SearchPlaceCandidatesUseCase(provider)

    @Bean
    fun updatePlaceBookmarkUseCase(updatePlaceBookmarkPort: UpdatePlaceBookmarkPort): UpdatePlaceBookmarkUseCase =
        UpdatePlaceBookmarkUseCase(updatePlaceBookmarkPort)

    @Bean
    fun updatePlaceMemoUseCase(updatePlaceMemoPort: UpdatePlaceMemoPort): UpdatePlaceMemoUseCase =
        UpdatePlaceMemoUseCase(updatePlaceMemoPort)

    @Bean
    fun getPlaceDetailUseCase(placeDetailQueryPort: PlaceDetailQueryPort): GetPlaceDetailUseCase =
        GetPlaceDetailUseCase(placeDetailQueryPort)
}
