package org.every.nook.api.infrastructure.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort
import org.every.nook.api.infrastructure.place.KakaoPlaceMapper
import org.every.nook.api.infrastructure.place.KakaoPlaceProperties
import org.every.nook.api.infrastructure.place.KakaoPlaceSearchProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(KakaoPlaceProperties::class)
class PlaceSearchConfig {
    @Bean
    fun kakaoPlaceMapper(): KakaoPlaceMapper = KakaoPlaceMapper()

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

    @Bean
    fun placeSearchProvider(
        @Qualifier("kakaoPlaceRestClient") restClient: RestClient,
        properties: KakaoPlaceProperties,
        mapper: KakaoPlaceMapper,
    ): PlaceSearchProvider = KakaoPlaceSearchProvider(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
        mapper = mapper,
    )

    @Bean
    fun searchPlaceCandidatesUseCase(provider: PlaceSearchProvider): SearchPlaceCandidatesUseCase =
        SearchPlaceCandidatesUseCase(provider)

    @Bean
    fun updatePlaceBookmarkUseCase(updatePlaceBookmarkPort: UpdatePlaceBookmarkPort): UpdatePlaceBookmarkUseCase =
        UpdatePlaceBookmarkUseCase(updatePlaceBookmarkPort)
}
