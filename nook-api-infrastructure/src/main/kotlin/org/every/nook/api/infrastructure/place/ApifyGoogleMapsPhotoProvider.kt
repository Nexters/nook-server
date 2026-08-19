package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ApifyGoogleMapsPhotoProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: ApifyGoogleMapsProperties,
    private val mediaStorage: PostMediaStoragePort,
    private val responseCache: ScrapingProviderResponseCache? = null,
) : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? = fetchAll(listOf(request)).single()

    override fun fetchAll(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> {
        if (requests.isEmpty()) return emptyList()
        if (properties.apiToken.isBlank()) {
            logger.warn("Apify Google Maps skipped: reason=missing_api_token")
            return List(requests.size) { null }
        }
        return requests.chunked(properties.batchSize).flatMap(::fetchBatch)
    }

    private fun fetchBatch(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> {
        val input = mapOf(
            "searchStringsArray" to requests.map { it.place.actorQuery() },
            "maxCrawledPlacesPerSearch" to 1,
            "maxImages" to MAX_PHOTO_COUNT,
            "scrapePlaceDetailPage" to true,
            "scrapeImageAuthors" to false,
            "language" to "ko",
        )
        val response = restClient.post()
            .uri("/v2/acts/{actorId}/run-sync-get-dataset-items?format=json&clean=true", properties.actorId)
            .contentType(MediaType.APPLICATION_JSON)
            .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
            .body(input)
            .retrieve()
            .body(String::class.java)
        response?.let { responseCache?.save(PROVIDER, SOURCE_TYPE, input.hashCode().toString(), it) }
        val places = parsePlaces(response)
        return requests.map { request ->
            places.bestMatch(request.place)?.let { match -> storePhotos(request, match.imageUrls) }
        }
    }

    private fun parsePlaces(body: String?): List<GoogleMapsPlace> {
        val root = body?.let(objectMapper::readTree) ?: return emptyList()
        if (!root.isArray) return emptyList()
        return root.mapNotNull { node ->
            val name = node.text("title", "name") ?: return@mapNotNull null
            GoogleMapsPlace(
                placeId = node.text("placeId"),
                name = name,
                address = node.text("address", "street") ?: "",
                latitude = node.path("location").decimal("lat") ?: node.decimal("latitude"),
                longitude = node.path("location").decimal("lng") ?: node.decimal("longitude"),
                imageUrls = node.stringList("imageUrls").ifEmpty {
                    listOfNotNull(node.text("imageUrl"))
                },
            )
        }
    }

    private fun List<GoogleMapsPlace>.bestMatch(place: PlaceCandidate): GoogleMapsPlace? {
        place.googlePlaceId?.let { googlePlaceId ->
            firstOrNull { it.placeId == googlePlaceId }?.let { return it }
        }
        return filter { it.name.matches(place.name) }
            .filter { candidate ->
                candidate.address.matches(place.address) ||
                    candidate.distanceMeters(place) <= MAX_DISTANCE_METERS
            }
            .minByOrNull { it.distanceMeters(place) }
    }

    private fun storePhotos(request: PlaceThumbnailProvider.Request, imageUrls: List<String>): PlaceSupplement? {
        val storedUrls = imageUrls.distinct().take(MAX_PHOTO_COUNT).mapIndexedNotNull { sequence, url ->
            runCatching {
                mediaStorage.store(PostMedia(PostMedia.MediaType.IMAGE, url, sequence)).url
            }.onFailure { exception ->
                logger.warn(
                    "Apify Google Maps photo storage failed: externalPlaceId={}, sequence={}",
                    request.place.externalPlaceId,
                    sequence,
                    exception,
                )
            }.getOrNull()
        }
        return storedUrls.takeIf(List<String>::isNotEmpty)?.let {
            PlaceSupplement(openingHours = null, photoUrls = it)
        }
    }

    private fun PlaceCandidate.actorQuery(): String = googlePlaceId?.let { "place_id:$it" } ?: "$name $address"

    private fun String.matches(other: String): Boolean {
        val left = normalize()
        val right = other.normalize()
        return left == right || left.contains(right) || right.contains(left)
    }

    private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)

    private fun GoogleMapsPlace.distanceMeters(place: PlaceCandidate): Double {
        val candidateLatitude = latitude ?: return Double.MAX_VALUE
        val candidateLongitude = longitude ?: return Double.MAX_VALUE
        val latitudeDelta = Math.toRadians(candidateLatitude.toDouble() - place.latitude.toDouble())
        val longitudeDelta = Math.toRadians(candidateLongitude.toDouble() - place.longitude.toDouble())
        val startLatitude = Math.toRadians(place.latitude.toDouble())
        val endLatitude = Math.toRadians(candidateLatitude.toDouble())
        val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    private data class GoogleMapsPlace(
        val placeId: String?,
        val name: String,
        val address: String,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val imageUrls: List<String>,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(ApifyGoogleMapsPhotoProvider::class.java)
        const val AUTHORIZATION = "Authorization"
        const val PROVIDER = "APIFY_GOOGLE_MAPS"
        const val SOURCE_TYPE = "GOOGLE_MAPS_PHOTO"
        const val MAX_PHOTO_COUNT = 6
        const val MAX_DISTANCE_METERS = 300.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

private fun JsonNode.text(vararg names: String): String? = names.asSequence()
    .map(::path)
    .firstOrNull { !it.isMissingNode && !it.isNull }
    ?.asString()
    ?.takeIf(String::isNotBlank)

private fun JsonNode.decimal(vararg names: String): BigDecimal? = names.asSequence()
    .map(::path)
    .firstOrNull { !it.isMissingNode && !it.isNull }
    ?.asString()
    ?.toBigDecimalOrNull()

private fun JsonNode.stringList(name: String): List<String> = path(name)
    .takeIf(JsonNode::isArray)
    ?.mapNotNull { it.asString().takeIf(String::isNotBlank) }
    .orEmpty()
