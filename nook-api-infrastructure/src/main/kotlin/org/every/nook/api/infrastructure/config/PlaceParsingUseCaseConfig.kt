package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.application.place.ProcessNextPlaceParsingJobUseCase
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PlaceParsingUseCaseConfig {
    @Bean
    fun processNextPlaceParsingJobUseCase(
        jobPort: PlaceParsingJobPort,
        clueExtractor: PlaceClueExtractor,
        searchPlaceCandidates: SearchPlaceCandidatesUseCase,
    ): ProcessNextPlaceParsingJobUseCase = ProcessNextPlaceParsingJobUseCase(
        jobPort = jobPort,
        clueExtractor = clueExtractor,
        searchPlaceCandidates = searchPlaceCandidates,
    )
}
