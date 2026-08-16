package org.every.nook.api.infrastructure.config

import mu.KotlinLogging
import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.infrastructure.place.FixedPlaceThumbnailProvider
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProperties
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProvider
import org.every.nook.api.infrastructure.place.PlaceThumbnailProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(GooglePlacePhotoProperties::class, PlaceThumbnailProperties::class)
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

    @Bean
    fun placeThumbnailProvider(
        @Qualifier("googlePlacePhotoRestClient")
        googlePlacePhotoRestClient: RestClient,
        googleProperties: GooglePlacePhotoProperties,
        thumbnailProperties: PlaceThumbnailProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
    ): PlaceThumbnailProvider = when (thumbnailProperties.provider) {
        PlaceThumbnailProperties.Provider.FIXED -> {
            logger.info { "Place thumbnail provider selected: provider=fixed" }
            FixedPlaceThumbnailProvider(thumbnailProperties.fixedUrl)
        }

        PlaceThumbnailProperties.Provider.GOOGLE -> googleProvider(
            googlePlacePhotoRestClient,
            googleProperties,
            mediaStorage,
        )

        PlaceThumbnailProperties.Provider.DISABLED -> {
            logger.info { "Place thumbnail provider disabled: reason=provider_disabled" }
            NoOpPlaceThumbnailProvider
        }
    }

    private fun googleProvider(
        restClient: RestClient,
        properties: GooglePlacePhotoProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
    ): PlaceThumbnailProvider {
        if (!properties.enabled) {
            logger.warn { "Place thumbnail provider disabled: reason=google_place_photo_disabled" }
            return NoOpPlaceThumbnailProvider
        }
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

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
