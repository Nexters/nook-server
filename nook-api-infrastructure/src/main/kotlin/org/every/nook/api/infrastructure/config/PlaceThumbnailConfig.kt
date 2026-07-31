package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.place.NoOpPlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProperties
import org.every.nook.api.infrastructure.place.GooglePlacePhotoProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(GooglePlacePhotoProperties::class)
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
        properties: GooglePlacePhotoProperties,
        mediaStorage: ObjectProvider<PostMediaStoragePort>,
    ): PlaceThumbnailProvider {
        val storage = mediaStorage.ifAvailable ?: return NoOpPlaceThumbnailProvider
        return if (properties.enabled) {
            GooglePlacePhotoProvider(googlePlacePhotoRestClient, properties, storage)
        } else {
            NoOpPlaceThumbnailProvider
        }
    }
}
