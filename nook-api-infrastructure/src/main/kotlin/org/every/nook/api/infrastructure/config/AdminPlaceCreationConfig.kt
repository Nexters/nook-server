package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.AdminPlaceAddressResolver
import org.every.nook.api.application.admin.AdminPlaceCreationPort
import org.every.nook.api.application.admin.CreateAdminPlaceUseCase
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.infrastructure.place.KakaoAdminPlaceAddressResolver
import org.every.nook.api.infrastructure.place.KakaoPlaceProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
class AdminPlaceCreationConfig {
    @Bean
    fun adminPlaceAddressResolver(
        @Qualifier("kakaoPlaceRestClient") restClient: RestClient,
        properties: KakaoPlaceProperties,
    ): AdminPlaceAddressResolver = KakaoAdminPlaceAddressResolver(
        restClient = restClient,
        objectMapper = jacksonObjectMapper(),
        properties = properties,
    )

    @Bean
    fun createAdminPlaceUseCase(
        addressResolver: AdminPlaceAddressResolver,
        creationPort: AdminPlaceCreationPort,
        tagCatalogPort: PlaceTagCatalogQueryPort,
    ) = CreateAdminPlaceUseCase(addressResolver, creationPort, tagCatalogPort)
}
