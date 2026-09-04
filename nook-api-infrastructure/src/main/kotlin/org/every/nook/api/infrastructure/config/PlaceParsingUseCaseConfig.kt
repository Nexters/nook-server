package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.place.ManualPlaceCandidateSearchPort
import org.every.nook.api.application.place.PlaceCandidateSelector
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceImageUrlPort
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.application.place.PlaceTagBackfillPort
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.application.place.PlaceTagExtractor
import org.every.nook.api.application.place.PlaceTagSourcePort
import org.every.nook.api.application.place.PlaceTagUpdatePort
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailUpdatePort
import org.every.nook.api.application.place.ProcessPlaceParsingJobUseCase
import org.every.nook.api.application.place.RebuildPlaceTagsUseCase
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.post.PostTitleSelector
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.ProcessingTracePort
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PlaceParsingProperties::class)
class PlaceParsingUseCaseConfig {
    @Bean
    fun processPlaceParsingJobUseCase(
        jobPort: PlaceParsingJobPort,
        imageUrlPort: PlaceImageUrlPort,
        imageTextExtractor: ImageTextExtractor,
        clueExtractor: PlaceClueExtractor,
        searchPlaceCandidates: SearchPlaceCandidatesUseCase,
        manualPlaceCandidateSearchPort: ManualPlaceCandidateSearchPort,
        candidateSelector: PlaceCandidateSelector,
        titleSelector: PostTitleSelector,
        processingMetrics: ObjectProvider<ProcessingMetrics>,
        processingTracePort: ProcessingTracePort,
        properties: PlaceParsingProperties,
    ): ProcessPlaceParsingJobUseCase = ProcessPlaceParsingJobUseCase(
        jobPort = jobPort,
        imageUrlPort = imageUrlPort,
        imageTextExtractor = imageTextExtractor,
        clueExtractor = clueExtractor,
        searchPlaceCandidates = searchPlaceCandidates,
        manualPlaceCandidateSearchPort = manualPlaceCandidateSearchPort,
        candidateSelector = candidateSelector,
        titleSelector = titleSelector,
        retryBackoffs = properties.retryBackoffs,
        processingTimeout = properties.processingTimeout,
        imageOcrConcurrency = properties.imageOcrConcurrency,
        metrics = processingMetrics.ifAvailable ?: NoOpProcessingMetrics,
        tracePort = processingTracePort,
    )

    @Bean
    fun storePlaceThumbnailUseCase(
        thumbnailProvider: PlaceThumbnailProvider,
        thumbnailUpdatePort: PlaceThumbnailUpdatePort,
        processingMetrics: ObjectProvider<ProcessingMetrics>,
    ): StorePlaceThumbnailUseCase = StorePlaceThumbnailUseCase(
        thumbnailProvider = thumbnailProvider,
        updatePort = thumbnailUpdatePort,
        metrics = processingMetrics.ifAvailable ?: NoOpProcessingMetrics,
    )

    @Bean
    fun storePlaceTagsUseCase(
        sourcePort: PlaceTagSourcePort,
        extractor: PlaceTagExtractor,
        updatePort: PlaceTagUpdatePort,
        catalogPort: PlaceTagCatalogQueryPort,
    ): StorePlaceTagsUseCase = StorePlaceTagsUseCase(sourcePort, extractor, updatePort, catalogPort)

    @Bean
    fun rebuildPlaceTagsUseCase(
        backfillPort: PlaceTagBackfillPort,
        storePlaceTags: StorePlaceTagsUseCase,
    ): RebuildPlaceTagsUseCase = RebuildPlaceTagsUseCase(backfillPort, storePlaceTags)

    @Bean
    fun findOutstandingPlaceParsingJobsUseCase(
        jobPort: PlaceParsingJobPort,
        properties: PlaceParsingProperties,
        @Value("\${parsing.dispatcher-batch-size:20}") batchSize: Int,
    ): FindOutstandingPlaceParsingJobsUseCase = FindOutstandingPlaceParsingJobsUseCase(
        jobPort = jobPort,
        processingTimeout = properties.processingTimeout,
        batchSize = batchSize,
    )
}
