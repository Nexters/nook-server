package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceOpeningPeriod
import org.every.nook.api.application.place.PlaceOpeningPoint
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GooglePlacePhotoProvider(
    private val restClient: RestClient,
    private val properties: GooglePlacePhotoProperties,
    private val mediaStorage: PostMediaStoragePort,
) : PlaceThumbnailProvider {
    override fun fetch(place: PlaceCandidate): PlaceSupplement? {
        val shouldSkip = !properties.enabled ||
            properties.apiKey.isBlank()
        return if (shouldSkip) {
            logger.warn {
                "Google place photo skipped: reason=invalid_configuration, enabled=${properties.enabled}, " +
                    "apiKeyConfigured=${properties.apiKey.isNotBlank()}"
            }
            null
        } else {
            runCatching {
                logger.info {
                    "Google place photo search started: provider=${place.provider}, " +
                        "externalPlaceId=${place.externalPlaceId}, name=${place.name}"
                }
                val googlePlace = searchPlace(place)
                if (googlePlace == null) {
                    logger.info {
                        "Google place supplement skipped: reason=place_not_matched, provider=${place.provider}, " +
                            "externalPlaceId=${place.externalPlaceId}, name=${place.name}"
                    }
                    null
                } else {
                    val photoUrls = googlePlace.photos.orEmpty()
                        .mapNotNull(GooglePhoto::name)
                        .distinct()
                        .take(PlaceSupplement.MAX_PHOTO_COUNT)
                        .mapIndexedNotNull { sequence, photoName -> storePhoto(photoName, sequence, place) }
                    PlaceSupplement(
                        openingHours = googlePlace.toOpeningHours(),
                        photoUrls = photoUrls,
                    )
                }
            }.onFailure { exception ->
                logger.warn(exception) {
                    "Google place photo fetch failed: provider=${place.provider}, " +
                        "externalPlaceId=${place.externalPlaceId}, name=${place.name}"
                }
            }.getOrNull()
        }
    }

    private fun searchPlace(place: PlaceCandidate): GooglePlace? {
        val response = restClient.post()
            .uri("/v1/places:searchText")
            .contentType(MediaType.APPLICATION_JSON)
            .header(API_KEY_HEADER, properties.apiKey)
            .header(FIELD_MASK_HEADER, SEARCH_FIELD_MASK)
            .body(TextSearchRequest(textQuery = "${place.name} ${place.address}".trim(), languageCode = "ko"))
            .retrieve()
            .body(TextSearchResponse::class.java)
        val matched = response?.places.orEmpty().firstOrNull { it.matches(place) }
        logger.info {
            "Google place photo search completed: provider=${place.provider}, " +
                "externalPlaceId=${place.externalPlaceId}, googlePlaceCount=${response?.places.orEmpty().size}, " +
                "matched=${matched != null}"
        }
        return matched
    }

    private fun GooglePlace.matches(candidate: PlaceCandidate): Boolean {
        val googleName = displayName?.text?.normalize() ?: return false
        val candidateName = candidate.name.normalize()
        val nameMatches = googleName.contains(candidateName) || candidateName.contains(googleName)
        val addressMatches = formattedAddress?.normalize()?.let { googleAddress ->
            val candidateAddress = candidate.address.normalize()
            googleAddress.contains(candidateAddress) || candidateAddress.contains(googleAddress)
        } ?: false
        val googleLocation = location ?: return false
        val distance = distanceMeters(
            candidate.latitude,
            candidate.longitude,
            BigDecimal.valueOf(googleLocation.latitude),
            BigDecimal.valueOf(googleLocation.longitude),
        )
        val locationMatches = distance <= CLOSE_MATCH_DISTANCE_METERS ||
            (addressMatches && distance <= MAX_MATCH_DISTANCE_METERS)
        return nameMatches && locationMatches
    }

    private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)

    private fun storePhoto(photoName: String, sequence: Int, place: PlaceCandidate): String? = runCatching {
        fetchPhotoUri(photoName)?.let { photoUri ->
            mediaStorage.store(PostMedia(PostMedia.MediaType.IMAGE, photoUri, sequence)).url
        }
    }.onFailure { exception ->
        logger.warn(exception) {
            "Google place photo storage failed: provider=${place.provider}, " +
                "externalPlaceId=${place.externalPlaceId}, sequence=$sequence"
        }
    }.getOrNull()

    private fun GooglePlace.toOpeningHours(): PlaceOpeningHours? {
        val hours = regularOpeningHours ?: return null
        val zone = timeZone?.id ?: return null
        return PlaceOpeningHours(
            timeZone = zone,
            periods = hours.periods.orEmpty().mapNotNull { period ->
                val open = period.open?.toPoint() ?: return@mapNotNull null
                PlaceOpeningPeriod(open, period.close?.toPoint())
            },
            weekdayDescriptions = hours.weekdayDescriptions.orEmpty(),
        )
    }

    private fun GooglePoint.toPoint(): PlaceOpeningPoint? {
        val validDay = day ?: return null
        return runCatching { PlaceOpeningPoint(validDay, hour ?: 0, minute ?: 0) }.getOrNull()
    }

    private fun fetchPhotoUri(photoName: String): String? {
        val photoUri = restClient.get()
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
        logger.info { "Google place photo media completed: photoUriFound=${photoUri != null}" }
        return photoUri
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TextSearchResponse(val places: List<GooglePlace>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePlace(
        val displayName: LocalizedText? = null,
        val formattedAddress: String? = null,
        val location: GoogleLocation? = null,
        val timeZone: GoogleTimeZone? = null,
        val regularOpeningHours: GoogleOpeningHours? = null,
        val photos: List<GooglePhoto>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class LocalizedText(val text: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GoogleLocation(val latitude: Double = 0.0, val longitude: Double = 0.0)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GoogleTimeZone(val id: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GoogleOpeningHours(
        val periods: List<GooglePeriod>? = null,
        val weekdayDescriptions: List<String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePeriod(val open: GooglePoint? = null, val close: GooglePoint? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePoint(val day: Int? = null, val hour: Int? = null, val minute: Int? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePhoto(val name: String? = null)

    private data class TextSearchRequest(val textQuery: String, val languageCode: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PhotoMediaResponse(val photoUri: String? = null)

    private companion object {
        val logger = KotlinLogging.logger {}
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        const val SEARCH_FIELD_MASK =
            "places.displayName,places.formattedAddress,places.location,places.timeZone," +
                "places.regularOpeningHours,places.photos.name"
        const val MAX_MATCH_DISTANCE_METERS = 500.0
        const val CLOSE_MATCH_DISTANCE_METERS = 100.0
        const val EARTH_RADIUS_METERS = 6_371_000.0

        fun distanceMeters(lat1: BigDecimal, lon1: BigDecimal, lat2: BigDecimal, lon2: BigDecimal): Double {
            val latitudeDelta = Math.toRadians(lat2.toDouble() - lat1.toDouble())
            val longitudeDelta = Math.toRadians(lon2.toDouble() - lon1.toDouble())
            val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
                cos(Math.toRadians(lat1.toDouble())) * cos(Math.toRadians(lat2.toDouble())) *
                sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
            return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
