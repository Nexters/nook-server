package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.place.GetMapPlacesUseCase
import org.every.nook.api.application.place.GetRecentPlacesUseCase
import org.every.nook.api.application.place.SearchSavedPlacesUseCase
import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.every.nook.api.application.place.port.SavedPlaceSearchPort
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
    fun searchSavedPlacesUseCase(
        savedPlaceSearchPort: SavedPlaceSearchPort,
        groupOwnershipPort: GroupOwnershipPort,
    ): SearchSavedPlacesUseCase = SearchSavedPlacesUseCase(savedPlaceSearchPort, groupOwnershipPort)
}
