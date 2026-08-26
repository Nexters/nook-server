package org.every.nook.api.infrastructure.config

import mu.KotlinLogging
import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.place.ApifyGoogleMapsPhotoProvider
import org.every.nook.api.infrastructure.place.ApifyGoogleMapsProperties
import org.every.nook.api.infrastructure.place.ApifyNaverPlacePhotoProperties
import org.every.nook.api.infrastructure.place.ApifyNaverPlacePhotoProvider
import org.every.nook.api.infrastructure.place.FixedPlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProperties
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProvider
import org.every.nook.api.infrastructure.place.PlaceThumbnailProperties
import org.every.nook.api.infrastructure.place.PlaceThumbnailProviderType
import org.every.nook.api.infrastructure.place.PostMediaPlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.RuntimePlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.toProviderChain
import org.every.nook.api.infrastructure.storage.MediaStorageProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

@Configuration
@EnableConfigurationProperties(
    GooglePlacePhotoProperties::class,
    PlaceThumbnailProperties::class,
    ApifyGoogleMapsProperties::class,
    ApifyNaverPlacePhotoProperties::class,
)
class PlaceThumbnailConfig {
    @Bean("googlePlacePhotoRestClient")
    fun googlePlacePhotoRestClient(properties: GooglePlacePhotoProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean("apifyGoogleMapsRestClient")
    fun apifyGoogleMapsRestClient(properties: ApifyGoogleMapsProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean("apifyNaverPlacePhotoRestClient")
    fun apifyNaverPlacePhotoRestClient(properties: ApifyNaverPlacePhotoProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean
    fun placeThumbnailProvider(
        @Qualifier("googlePlacePhotoRestClient")
        googlePlacePhotoRestClient: RestClient,
        @Qualifier("apifyGoogleMapsRestClient")
        apifyGoogleMapsRestClient: RestClient,
        @Qualifier("apifyNaverPlacePhotoRestClient")
        apifyNaverPlacePhotoRestClient: RestClient,
        googleProperties: GooglePlacePhotoProperties,
        apifyProperties: ApifyGoogleMapsProperties,
        apifyNaverProperties: ApifyNaverPlacePhotoProperties,
        thumbnailProperties: PlaceThumbnailProperties,
        configurationReader: RuntimeConfigurationReader,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
        mediaRepository: ObjectProvider<PostMediaJpaRepository>,
        mediaStorageProperties: ObjectProvider<MediaStorageProperties>,
        responseCache: ObjectProvider<ScrapingProviderResponseCache>,
        processingMetrics: ObjectProvider<ProcessingMetrics>,
    ): PlaceThumbnailProvider {
        val providers = mapOf(
            PlaceThumbnailProviderType.POST_MEDIA to postMediaProvider(
                thumbnailProperties = thumbnailProperties,
                mediaStorage = mediaStorage,
                mediaRepository = mediaRepository,
                mediaStorageProperties = mediaStorageProperties,
            ),
            PlaceThumbnailProviderType.APIFY_GOOGLE to apifyProvider(
                apifyGoogleMapsRestClient,
                apifyProperties,
                mediaStorage,
                responseCache.ifAvailable,
                processingMetrics.ifAvailable ?: NoOpProcessingMetrics,
            ),
            PlaceThumbnailProviderType.APIFY_NAVER_PLACE to apifyNaverProvider(
                apifyNaverPlacePhotoRestClient,
                apifyNaverProperties,
                mediaStorage,
                responseCache.ifAvailable,
                processingMetrics.ifAvailable ?: NoOpProcessingMetrics,
            ),
            PlaceThumbnailProviderType.GOOGLE to googleProvider(
                googlePlacePhotoRestClient,
                googleProperties,
                mediaStorage,
            ),
            PlaceThumbnailProviderType.FIXED to FixedPlaceThumbnailProvider(thumbnailProperties.fixedUrl),
        )
        return RuntimePlaceThumbnailProvider(
            providers = providers,
            configurationReader = configurationReader,
            legacyChain = thumbnailProperties.provider.toProviderChain(),
        )
    }

    private fun postMediaProvider(
        thumbnailProperties: PlaceThumbnailProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
        mediaRepository: ObjectProvider<PostMediaJpaRepository>,
        mediaStorageProperties: ObjectProvider<MediaStorageProperties>,
    ): PlaceThumbnailProvider {
        val storage = mediaStorage.ifAvailable
        val repository = mediaRepository.ifAvailable
        if (storage == null || repository == null) {
            logger.warn { "Post media place thumbnail provider disabled: reason=missing_dependency" }
            return NoOpPlaceThumbnailProvider
        }
        logger.info { "Place thumbnail provider selected: provider=post_media" }
        return PostMediaPlaceThumbnailProvider(
            mediaRepository = repository,
            mediaStorage = storage,
            storedMediaBaseUrl = mediaStorageProperties.ifAvailable?.cloudFrontBaseUrl,
            obsoleteFixedThumbnailUrl = thumbnailProperties.fixedUrl,
        )
    }

    private fun googleProvider(
        restClient: RestClient,
        properties: GooglePlacePhotoProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
    ): PlaceThumbnailProvider {
        val storage = mediaStorage.ifAvailable ?: run {
            logger.warn { "Place thumbnail provider disabled: reason=missing_media_storage" }
            return NoOpPlaceThumbnailProvider
        }
        logger.info {
            "Google place photo provider enabled: baseUrl=${properties.baseUrl}, " +
                "maxWidthPx=${properties.maxWidthPx}"
        }
        return GooglePlacePhotoProvider(restClient, properties, storage)
    }

    private fun apifyProvider(
        restClient: RestClient,
        properties: ApifyGoogleMapsProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
        responseCache: ScrapingProviderResponseCache?,
        processingMetrics: ProcessingMetrics,
    ): PlaceThumbnailProvider {
        val storage = mediaStorage.ifAvailable ?: run {
            logger.warn { "Apify Google Maps provider disabled: reason=missing_media_storage" }
            return NoOpPlaceThumbnailProvider
        }
        return ApifyGoogleMapsPhotoProvider(
            restClient = restClient,
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            mediaStorage = storage,
            responseCache = responseCache,
            metrics = processingMetrics,
        )
    }

    private fun apifyNaverProvider(
        restClient: RestClient,
        properties: ApifyNaverPlacePhotoProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
        responseCache: ScrapingProviderResponseCache?,
        processingMetrics: ProcessingMetrics,
    ): PlaceThumbnailProvider {
        val storage = mediaStorage.ifAvailable ?: run {
            logger.warn { "Apify Naver place provider disabled: reason=missing_media_storage" }
            return NoOpPlaceThumbnailProvider
        }
        return ApifyNaverPlacePhotoProvider(
            restClient = restClient,
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            mediaStorage = storage,
            responseCache = responseCache,
            metrics = processingMetrics,
        )
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
