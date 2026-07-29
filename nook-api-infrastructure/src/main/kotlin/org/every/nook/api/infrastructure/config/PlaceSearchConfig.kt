package org.every.nook.api.infrastructure.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.place.GetPlaceDetailUseCase
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.application.place.port.PlaceDetailQueryPort
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort
import org.every.nook.api.infrastructure.place.CompositePlaceSearchProvider
import org.every.nook.api.infrastructure.place.KakaoPlaceMapper
import org.every.nook.api.infrastructure.place.KakaoPlaceProperties
import org.every.nook.api.infrastructure.place.KakaoPlaceSearchProvider
import org.every.nook.api.infrastructure.place.NaverPlaceMapper
import org.every.nook.api.infrastructure.place.NaverPlaceProperties
import org.every.nook.api.infrastructure.place.NaverPlaceSearchProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.util.concurrent.Executor
import java.util.concurrent.Executors

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

    @Bean(destroyMethod = "shutdown")
    fun placeSearchExecutor(): java.util.concurrent.ExecutorService =
        Executors.newFixedThreadPool(PLACE_SEARCH_THREAD_COUNT)

    @Bean
    @Primary
    fun placeSearchProvider(
        @Qualifier("kakaoPlaceSearchProvider") kakaoProvider: PlaceSearchProvider,
        @Qualifier("naverPlaceSearchProvider") naverProvider: PlaceSearchProvider,
        @Qualifier("placeSearchExecutor") placeSearchExecutor: Executor,
    ): PlaceSearchProvider = CompositePlaceSearchProvider(
        providers = listOf(
            CompositePlaceSearchProvider.NamedPlaceSearchProvider("KAKAO", kakaoProvider),
            CompositePlaceSearchProvider.NamedPlaceSearchProvider("NAVER", naverProvider),
        ),
        executor = placeSearchExecutor,
    )

    @Bean
    fun searchPlaceCandidatesUseCase(
        @Qualifier("placeSearchProvider") provider: PlaceSearchProvider,
    ): SearchPlaceCandidatesUseCase = SearchPlaceCandidatesUseCase(provider)

    @Bean
    fun updatePlaceBookmarkUseCase(updatePlaceBookmarkPort: UpdatePlaceBookmarkPort): UpdatePlaceBookmarkUseCase =
        UpdatePlaceBookmarkUseCase(updatePlaceBookmarkPort)

    @Bean
    fun getPlaceDetailUseCase(placeDetailQueryPort: PlaceDetailQueryPort): GetPlaceDetailUseCase =
        GetPlaceDetailUseCase(placeDetailQueryPort)

    private companion object {
        const val PLACE_SEARCH_THREAD_COUNT = 2
    }
}
