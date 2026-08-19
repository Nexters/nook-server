package org.every.nook.api.infrastructure.config

import mu.KotlinLogging
import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.place.ApifyNaverPlaceProperties
import org.every.nook.api.infrastructure.place.ApifyNaverPlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.FixedPlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProperties
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProvider
import org.every.nook.api.infrastructure.place.PlaceThumbnailProperties
import org.every.nook.api.infrastructure.place.PlaceThumbnailProviderType
import org.every.nook.api.infrastructure.place.PostMediaPlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.RuntimePlaceThumbnailProvider
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
    ApifyNaverPlaceProperties::class,
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

    @Bean("apifyNaverPlaceRestClient")
    fun apifyNaverPlaceRestClient(properties: ApifyNaverPlaceProperties): RestClient {
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
        @Qualifier("apifyNaverPlaceRestClient")
        apifyNaverPlaceRestClient: RestClient,
        googleProperties: GooglePlacePhotoProperties,
        apifyProperties: ApifyNaverPlaceProperties,
        thumbnailProperties: PlaceThumbnailProperties,
        configurationReader: RuntimeConfigurationReader,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
        mediaRepository: ObjectProvider<PostMediaJpaRepository>,
        mediaStorageProperties: ObjectProvider<MediaStorageProperties>,
        responseCache: ObjectProvider<ScrapingProviderResponseCache>,
    ): PlaceThumbnailProvider {
        val providers = mapOf(
            PlaceThumbnailProviderType.POST_MEDIA to postMediaProvider(
                thumbnailProperties = thumbnailProperties,
                mediaStorage = mediaStorage,
                mediaRepository = mediaRepository,
                mediaStorageProperties = mediaStorageProperties,
            ),
            PlaceThumbnailProviderType.APIFY_NAVER to apifyProvider(
                apifyNaverPlaceRestClient,
                apifyProperties,
                mediaStorage,
                responseCache.ifAvailable,
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
            legacyChain = thumbnailProperties.provider.toChain(),
        )
    }

    private fun PlaceThumbnailProperties.Provider.toChain(): List<PlaceThumbnailProviderType> = when (this) {
        PlaceThumbnailProperties.Provider.POST_MEDIA -> listOf(PlaceThumbnailProviderType.POST_MEDIA)
        PlaceThumbnailProperties.Provider.FIXED -> listOf(PlaceThumbnailProviderType.FIXED)
        PlaceThumbnailProperties.Provider.GOOGLE -> listOf(PlaceThumbnailProviderType.GOOGLE)
        PlaceThumbnailProperties.Provider.DISABLED -> emptyList()
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
        properties: ApifyNaverPlaceProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
        responseCache: ScrapingProviderResponseCache?,
    ): PlaceThumbnailProvider {
        val storage = mediaStorage.ifAvailable ?: run {
            logger.warn { "Apify Naver place provider disabled: reason=missing_media_storage" }
            return NoOpPlaceThumbnailProvider
        }
        return ApifyNaverPlaceThumbnailProvider(
            restClient = restClient,
            objectMapper = jacksonObjectMapper(),
            properties = properties,
            mediaStorage = storage,
            responseCache = responseCache,
        )
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
