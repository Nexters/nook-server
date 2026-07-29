package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.GetMapPlacesUseCase
import org.every.nook.api.application.place.GetRecentPlacesUseCase
import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PlaceMapQueryUseCaseConfig {
    @Bean
    fun getMapPlacesUseCase(placeMapQueryPort: PlaceMapQueryPort): GetMapPlacesUseCase =
        GetMapPlacesUseCase(placeMapQueryPort)

    @Bean
    fun getRecentPlacesUseCase(placeMapQueryPort: PlaceMapQueryPort): GetRecentPlacesUseCase =
        GetRecentPlacesUseCase(placeMapQueryPort)
}
