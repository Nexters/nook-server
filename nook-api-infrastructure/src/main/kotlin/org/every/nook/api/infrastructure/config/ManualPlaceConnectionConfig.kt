package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.ConnectPostPlaceUseCase
import org.every.nook.api.application.place.PagedPlaceSearchProvider
import org.every.nook.api.application.place.PlaceSelectionTokenPort
import org.every.nook.api.application.place.SearchPlacesUseCase
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.infrastructure.auth.JwtProperties
import org.every.nook.api.infrastructure.place.JwtPlaceSelectionTokenAdapter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ManualPlaceConnectionConfig {
    @Bean
    fun placeSelectionTokenPort(properties: JwtProperties, clock: Clock): PlaceSelectionTokenPort =
        JwtPlaceSelectionTokenAdapter(properties, clock)

    @Bean
    fun searchPlacesUseCase(
        @Qualifier("kakaoPlaceSearchProvider") provider: PagedPlaceSearchProvider,
        selectionTokenPort: PlaceSelectionTokenPort,
    ): SearchPlacesUseCase = SearchPlacesUseCase(provider, selectionTokenPort)

    @Bean
    fun connectPostPlaceUseCase(
        selectionTokenPort: PlaceSelectionTokenPort,
        connectPostPlacePort: ConnectPostPlacePort,
    ): ConnectPostPlaceUseCase = ConnectPostPlaceUseCase(selectionTokenPort, connectPostPlacePort)
}
