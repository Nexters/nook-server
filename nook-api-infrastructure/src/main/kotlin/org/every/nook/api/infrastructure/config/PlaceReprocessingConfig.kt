package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.GetPlaceReprocessingUseCase
import org.every.nook.api.application.admin.PlaceReprocessingPort
import org.every.nook.api.application.admin.PlaceReprocessingSourcePort
import org.every.nook.api.application.admin.ProcessPlaceReprocessingUseCase
import org.every.nook.api.application.admin.RequestPlaceReprocessingUseCase
import org.every.nook.api.application.place.PlaceTagSourcePort
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PlaceReprocessingConfig {
    @Bean
    fun requestPlaceReprocessingUseCase(port: PlaceReprocessingPort) = RequestPlaceReprocessingUseCase(port)

    @Bean
    fun getPlaceReprocessingUseCase(port: PlaceReprocessingPort) = GetPlaceReprocessingUseCase(port)

    @Bean
    fun processPlaceReprocessingUseCase(
        port: PlaceReprocessingPort,
        sources: PlaceReprocessingSourcePort,
        thumbnails: PlaceThumbnailProvider,
        storeTags: StorePlaceTagsUseCase,
        tagSources: PlaceTagSourcePort,
    ) = ProcessPlaceReprocessingUseCase(port, sources, thumbnails, storeTags, tagSources)
}
