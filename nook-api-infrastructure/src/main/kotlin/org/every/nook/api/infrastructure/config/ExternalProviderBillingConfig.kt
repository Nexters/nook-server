package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.providerusage.ExternalProviderBillingQueryPort
import org.every.nook.api.application.providerusage.ExternalProviderBillingSource
import org.every.nook.api.application.providerusage.ExternalProviderBillingStore
import org.every.nook.api.application.providerusage.GetExternalProviderBillingUseCase
import org.every.nook.api.application.providerusage.SyncExternalProviderBillingUseCase
import org.every.nook.api.infrastructure.instagram.ApifyProperties
import org.every.nook.api.infrastructure.place.ApifyGoogleMapsProperties
import org.every.nook.api.infrastructure.place.ApifyNaverPlacePhotoProperties
import org.every.nook.api.infrastructure.providerusage.ApifyBillingSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
class ExternalProviderBillingConfig {
    @Bean
    fun apifyBillingSource(
        apify: ApifyProperties,
        googleMaps: ApifyGoogleMapsProperties,
        naverPlace: ApifyNaverPlacePhotoProperties,
    ): ExternalProviderBillingSource {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(apify.connectTimeout)
            setReadTimeout(apify.readTimeout)
        }
        return ApifyBillingSource(
            restClient = RestClient.builder().baseUrl(apify.baseUrl).requestFactory(requestFactory).build(),
            objectMapper = jacksonObjectMapper(),
            apiToken = apify.apiToken,
            actors = listOf(
                ApifyBillingSource.Actor("INSTAGRAM_SCRAPER", apify.actorId),
                ApifyBillingSource.Actor("GOOGLE_MAPS_SCRAPER", googleMaps.actorId),
                ApifyBillingSource.Actor("NAVER_MAP_SEARCH_RESULTS_SCRAPER", naverPlace.searchActorId),
                ApifyBillingSource.Actor("NAVER_PLACE_PHOTO_SCRAPER", naverPlace.photoActorId),
            ),
        )
    }

    @Bean
    fun syncExternalProviderBillingUseCase(
        sources: List<ExternalProviderBillingSource>,
        store: ExternalProviderBillingStore,
    ) = SyncExternalProviderBillingUseCase(sources, store)

    @Bean
    fun getExternalProviderBillingUseCase(port: ExternalProviderBillingQueryPort) =
        GetExternalProviderBillingUseCase(port)
}
