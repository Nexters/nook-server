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
import org.every.nook.api.application.processing.debug
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.warn
import org.every.nook.api.domain.post.PostMedia
import org.slf4j.LoggerFactory
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
            eventLogger.logGoogleSkipped(place)
            logger.warn {
                "Google place photo skipped: reason=invalid_configuration, enabled=${properties.enabled}, " +
                    "apiKeyConfigured=${properties.apiKey.isNotBlank()}"
            }
            null
        } else {
            val startedAt = System.nanoTime()
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
                    val availablePhotos = googlePlace.photos.orEmpty()
                        .mapNotNull(GooglePhoto::name)
                        .distinct()
                        .take(PlaceSupplement.MAX_PHOTO_COUNT)
                    eventLogger.logGooglePhotoList(place, googlePlace.photos.orEmpty().size, availablePhotos.size)
                    val photoUrls = availablePhotos
                        .mapIndexedNotNull { sequence, photoName -> storePhoto(photoName, sequence, place) }
                    eventLogger.logGooglePhotoPipeline(place, availablePhotos.size, photoUrls.size)
                    PlaceSupplement(
                        openingHours = googlePlace.toOpeningHours(),
                        photoUrls = photoUrls,
                        googlePlaceId = googlePlace.placeId(),
                    )
                }
            }.onFailure { exception ->
                eventLogger.logGoogleFetchFailure(place, exception, startedAt)
                logger.warn(exception) {
                    "Google place photo fetch failed: provider=${place.provider}, " +
                        "externalPlaceId=${place.externalPlaceId}, name=${place.name}"
                }
            }.getOrNull()
        }
    }

    private fun searchPlace(place: PlaceCandidate): GooglePlace? {
        place.googlePlaceId?.let { return getPlace(it) }
        val startedAt = System.nanoTime()
        val response = restClient.post()
            .uri("/v1/places:searchText")
            .contentType(MediaType.APPLICATION_JSON)
            .header(API_KEY_HEADER, properties.apiKey)
            .header(FIELD_MASK_HEADER, SEARCH_FIELD_MASK)
            .body(
                TextSearchRequest(
                    textQuery = "${place.name} ${place.address}".trim(),
                    languageCode = "ko",
                    regionCode = "KR",
                    pageSize = SEARCH_PAGE_SIZE,
                    locationBias = LocationBias(
                        Circle(
                            center = GoogleLocation(place.latitude.toDouble(), place.longitude.toDouble()),
                            radius = LOCATION_BIAS_RADIUS_METERS,
                        ),
                    ),
                ),
            )
            .retrieve()
            .body(TextSearchResponse::class.java)
        val scored = response?.places.orEmpty().map { it to it.matchScore(place) }
        val matched = scored.maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_MATCH_SCORE }
            ?.first
        eventLogger.info(
            place.event(
                "google.place.match.completed",
                SEARCH_STAGE,
                if (matched == null) "empty" else "success",
                mapOf(
                    "event.duration_ms" to elapsedMillis(startedAt),
                    "google.place_candidate_count" to response?.places.orEmpty().size,
                    "google.place_matched" to (matched != null),
                    "google.place_candidate_scores" to scored.map { "${it.first.placeId()}:${it.second}" },
                    "google.place_selected_id" to matched?.placeId(),
                    "empty.reason" to if (matched == null) "place_not_matched" else null,
                ),
            ),
        )
        logger.info {
            "Google place photo search completed: provider=${place.provider}, " +
                "externalPlaceId=${place.externalPlaceId}, googlePlaceCount=${response?.places.orEmpty().size}, " +
                "matched=${matched != null}"
        }
        return matched
    }

    private fun getPlace(googlePlaceId: String): GooglePlace? = restClient.get()
        .uri("/v1/places/{placeId}", googlePlaceId)
        .header(API_KEY_HEADER, properties.apiKey)
        .header(FIELD_MASK_HEADER, DETAIL_FIELD_MASK)
        .retrieve()
        .body(GooglePlace::class.java)

    private fun GooglePlace.matchScore(candidate: PlaceCandidate): Int {
        val googleName = displayName?.text?.normalize() ?: return 0
        val candidateName = candidate.name.normalize()
        val nameScore = nameScore(googleName, candidateName, displayName.text.orEmpty(), candidate.name)
        val addressMatches = formattedAddress?.normalize()?.let { googleAddress ->
            val candidateAddress = candidate.address.normalize()
            googleAddress.contains(candidateAddress) || candidateAddress.contains(googleAddress)
        } ?: false
        val distance = location?.let { googleLocation ->
            distanceMeters(
                candidate.latitude,
                candidate.longitude,
                BigDecimal.valueOf(googleLocation.latitude),
                BigDecimal.valueOf(googleLocation.longitude),
            )
        }
        val addressScore = if (addressMatches) 30 else 0
        val distanceScore = when {
            distance == null -> 0
            distance <= CLOSE_MATCH_DISTANCE_METERS -> 25
            distance <= MAX_MATCH_DISTANCE_METERS -> 15
            distance <= FAR_MATCH_DISTANCE_METERS -> 5
            else -> 0
        }
        return nameScore + addressScore + distanceScore
    }

    private fun nameScore(googleName: String, candidateName: String, rawGoogleName: String, rawCandidateName: String) =
        when {
            googleName == candidateName -> EXACT_NAME_SCORE
            googleName.contains(candidateName) || candidateName.contains(googleName) -> CONTAINS_NAME_SCORE
            else -> tokenOverlap(rawGoogleName, rawCandidateName) * TOKEN_NAME_SCORE
        }

    private fun tokenOverlap(left: String, right: String): Int {
        val leftTokens = left.lowercase().split(Regex("[^가-힣a-z0-9]+")).filter { it.length >= 2 }.toSet()
        val rightTokens = right.lowercase().split(Regex("[^가-힣a-z0-9]+")).filter { it.length >= 2 }.toSet()
        return if (leftTokens.intersect(rightTokens).isEmpty()) 0 else 1
    }

    private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)

    private fun storePhoto(photoName: String, sequence: Int, place: PlaceCandidate): String? {
        val photoUri = runCatching { fetchPhotoUri(photoName, sequence, place) }
            .onFailure { exception ->
                eventLogger.warn(
                    place.photoEvent("google.photo.media.failed", PHOTO_MEDIA_STAGE, sequence, exception),
                    exception,
                )
            }.getOrNull() ?: return null
        val startedAt = System.nanoTime()
        return runCatching {
            mediaStorage.store(PostMedia(PostMedia.MediaType.IMAGE, photoUri, sequence)).url
        }.onSuccess {
            eventLogger.debug(
                place.event(
                    "google.photo.store.completed",
                    PHOTO_STORE_STAGE,
                    "success",
                    mapOf("event.duration_ms" to elapsedMillis(startedAt), "media.sequence" to sequence),
                ),
            )
        }.onFailure { exception ->
            eventLogger.warn(
                place.photoEvent("google.photo.store.failed", PHOTO_STORE_STAGE, sequence, exception, startedAt),
                exception,
            )
            logger.warn(exception) {
                "Google place photo storage failed: provider=${place.provider}, " +
                    "externalPlaceId=${place.externalPlaceId}, sequence=$sequence"
            }
        }.getOrNull()
    }

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

    private fun fetchPhotoUri(photoName: String, sequence: Int, place: PlaceCandidate): String? {
        val startedAt = System.nanoTime()
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
        eventLogger.debug(
            place.event(
                "google.photo.media.completed",
                PHOTO_MEDIA_STAGE,
                if (photoUri == null) "empty" else "success",
                mapOf(
                    "event.duration_ms" to elapsedMillis(startedAt),
                    "media.sequence" to sequence,
                    "google.photo_uri_found" to (photoUri != null),
                    "empty.reason" to if (photoUri == null) "photo_uri_missing" else null,
                ),
            ),
        )
        logger.info { "Google place photo media completed: photoUriFound=${photoUri != null}" }
        return photoUri
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TextSearchResponse(val places: List<GooglePlace>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GooglePlace(
        val id: String? = null,
        val name: String? = null,
        val displayName: LocalizedText? = null,
        val formattedAddress: String? = null,
        val location: GoogleLocation? = null,
        val timeZone: GoogleTimeZone? = null,
        val regularOpeningHours: GoogleOpeningHours? = null,
        val photos: List<GooglePhoto>? = null,
    ) {
        fun placeId(): String? = id
            ?: name?.removePrefix(PLACE_RESOURCE_PREFIX)?.takeIf(String::isNotBlank)
            ?: photos.orEmpty().asSequence()
                .mapNotNull(GooglePhoto::name)
                .mapNotNull { photoName ->
                    photoName.removePrefix(PLACE_RESOURCE_PREFIX).substringBefore(PHOTO_RESOURCE_SEPARATOR)
                        .takeIf(String::isNotBlank)
                }
                .firstOrNull()
    }

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

    private data class TextSearchRequest(
        val textQuery: String,
        val languageCode: String,
        val regionCode: String,
        val pageSize: Int,
        val locationBias: LocationBias,
    )
    private data class LocationBias(val circle: Circle)
    private data class Circle(val center: GoogleLocation, val radius: Double)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PhotoMediaResponse(val photoUri: String? = null)

    private companion object {
        val logger = KotlinLogging.logger {}
        val eventLogger = LoggerFactory.getLogger(GooglePlacePhotoProvider::class.java)
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        const val DETAIL_FIELD_MASK =
            "id,name,displayName,formattedAddress,location,timeZone,regularOpeningHours,photos.name"
        const val SEARCH_FIELD_MASK =
            "places.id,places.name,places.displayName,places.formattedAddress,places.location,places.timeZone," +
                "places.regularOpeningHours,places.photos.name"
        const val PLACE_RESOURCE_PREFIX = "places/"
        const val PHOTO_RESOURCE_SEPARATOR = "/photos/"
        const val SEARCH_PAGE_SIZE = 5
        const val LOCATION_BIAS_RADIUS_METERS = 1_000.0
        const val MIN_MATCH_SCORE = 45
        const val EXACT_NAME_SCORE = 60
        const val CONTAINS_NAME_SCORE = 45
        const val TOKEN_NAME_SCORE = 30
        const val MAX_MATCH_DISTANCE_METERS = 500.0
        const val FAR_MATCH_DISTANCE_METERS = 2_000.0
        const val CLOSE_MATCH_DISTANCE_METERS = 100.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val SEARCH_STAGE = "google-place-match"
        const val PHOTO_LIST_STAGE = "google-photo-list"
        const val PHOTO_MEDIA_STAGE = "google-photo-media"
        const val PHOTO_STORE_STAGE = "google-photo-store"

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
