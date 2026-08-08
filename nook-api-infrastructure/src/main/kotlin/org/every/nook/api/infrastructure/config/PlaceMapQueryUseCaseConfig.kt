package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.GetMapPlacesUseCase
import org.every.nook.api.application.place.GetRecentPlacesUseCase
import org.every.nook.api.application.place.SearchAllStoredPlacesUseCase
import org.every.nook.api.application.place.SearchMyStoredPlacesUseCase
import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.every.nook.api.application.place.port.SearchAllStoredPlacesPort
import org.every.nook.api.application.place.port.SearchMyStoredPlacesPort
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

    @Bean
    fun searchAllStoredPlacesUseCase(port: SearchAllStoredPlacesPort): SearchAllStoredPlacesUseCase =
        SearchAllStoredPlacesUseCase(port)

    @Bean
    fun searchMyStoredPlacesUseCase(port: SearchMyStoredPlacesPort): SearchMyStoredPlacesUseCase =
        SearchMyStoredPlacesUseCase(port)
}
