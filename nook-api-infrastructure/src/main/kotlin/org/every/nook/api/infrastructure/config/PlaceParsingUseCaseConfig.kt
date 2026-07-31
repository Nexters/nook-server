package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.place.PlaceCandidateSelector
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.ProcessPlaceParsingJobUseCase
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PlaceParsingProperties::class)
class PlaceParsingUseCaseConfig {
    @Bean
    fun processPlaceParsingJobUseCase(
        jobPort: PlaceParsingJobPort,
        clueExtractor: PlaceClueExtractor,
        searchPlaceCandidates: SearchPlaceCandidatesUseCase,
        candidateSelector: PlaceCandidateSelector,
        thumbnailProvider: PlaceThumbnailProvider,
        properties: PlaceParsingProperties,
    ): ProcessPlaceParsingJobUseCase = ProcessPlaceParsingJobUseCase(
        jobPort = jobPort,
        clueExtractor = clueExtractor,
        searchPlaceCandidates = searchPlaceCandidates,
        candidateSelector = candidateSelector,
        thumbnailProvider = thumbnailProvider,
        retryBackoffs = properties.retryBackoffs,
        processingTimeout = properties.processingTimeout,
    )

    @Bean
    fun findOutstandingPlaceParsingJobsUseCase(
        jobPort: PlaceParsingJobPort,
        properties: PlaceParsingProperties,
    ): FindOutstandingPlaceParsingJobsUseCase = FindOutstandingPlaceParsingJobsUseCase(
        jobPort = jobPort,
        processingTimeout = properties.processingTimeout,
    )
}
