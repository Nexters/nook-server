package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

class GooglePlacePhotoProvider(
    private val restClient: RestClient,
    private val properties: GooglePlacePhotoProperties,
    private val mediaStorage: PostMediaStoragePort,
) : PlaceThumbnailProvider {
    override fun fetchThumbnailUrl(place: PlaceCandidate): String? {
        val shouldSkip = !properties.enabled ||
            properties.apiKey.isBlank()
        return if (shouldSkip) {
            null
        } else {
            runCatching {
                searchPhotoName(place)
                    ?.let(::fetchPhotoUri)
                    ?.let { photoUri ->
                        mediaStorage.store(
                            PostMedia(PostMedia.MediaType.IMAGE, photoUri, GOOGLE_THUMBNAIL_SEQUENCE),
                        ).url
                    }
            }.onFailure { exception ->
                logger.warn(exception) {
                    "Google place photo fetch failed: provider=${place.provider}, " +
                        "externalPlaceId=${place.externalPlaceId}, name=${place.name}"
                }
            }.getOrNull()
        }
    }

    private fun searchPhotoName(place: PlaceCandidate): String? {
        val response = restClient.post()
            .uri("/v1/places:searchText")
            .contentType(MediaType.APPLICATION_JSON)
            .header(API_KEY_HEADER, properties.apiKey)
            .header(FIELD_MASK_HEADER, SEARCH_FIELD_MASK)
            .body(TextSearchRequest(textQuery = "${place.name} ${place.address}".trim()))
            .retrieve()
            .body(TextSearchResponse::class.java)
        return response?.places.orEmpty()
            .asSequence()
            .flatMap { it.photos.orEmpty() }
            .mapNotNull { it.name }
            .firstOrNull()
    }

    private fun fetchPhotoUri(photoName: String): String? = restClient.get()
        .uri { builder ->
            builder
                .path("/v1/$photoName/media")
                .queryParam("maxWidthPx", properties.maxWidthPx)
                .queryParam("skipHttpRedirect", true)
                .build()
        }
        .header(API_KEY_HEADER, properties.apiKey)
        .retrieve()
        .body(PhotoMediaResponse::class.java)
        ?.photoUri

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TextSearchResponse(val places: List<GooglePlace>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePlace(val photos: List<GooglePhoto>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePhoto(val name: String? = null)

    private data class TextSearchRequest(val textQuery: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PhotoMediaResponse(val photoUri: String? = null)

    private companion object {
        val logger = KotlinLogging.logger {}
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        const val SEARCH_FIELD_MASK = "places.photos.name"
        const val GOOGLE_THUMBNAIL_SEQUENCE = 0
    }
}
