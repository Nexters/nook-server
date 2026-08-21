package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.measure
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.util.concurrent.Executors
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
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
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
            "maxCrawledPlacesPerSearch" to MAX_SEARCH_RESULT_COUNT,
            "maxImages" to MAX_PHOTO_COUNT,
            "scrapePlaceDetailPage" to true,
            "scrapeImageAuthors" to false,
            "language" to "ko",
        )
        val response = measureThumbnailStage(metrics, clock, requests, ACTOR_STAGE) {
            restClient.post()
                .uri("/v2/acts/{actorId}/run-sync-get-dataset-items?format=json&clean=true", properties.actorId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
                .body(input)
                .retrieve()
                .body(String::class.java)
        }
        response?.let { responseCache?.save(PROVIDER, SOURCE_TYPE, input.hashCode().toString(), it) }
        val places = parsePlaces(response)
        val matches = requests.map { request -> places.bestMatch(request.place) }
        return measureThumbnailStage(metrics, clock, requests, IMAGE_STORE_STAGE) {
            storePhotos(requests, matches)
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
                category = node.text("categoryName") ?: node.stringList("categories").firstOrNull(),
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
        return filter { candidate -> candidate.isEligible(place) }
            .minWithOrNull(
                compareByDescending<GoogleMapsPlace> { it.name.matches(place.name) }
                    .thenByDescending { it.address.matchesAddress(place.address) }
                    .thenBy { it.category.conflictsWith(place.category) }
                    .thenBy { it.distanceMeters(place) },
            )
    }

    private fun GoogleMapsPlace.isEligible(place: PlaceCandidate): Boolean {
        val nameMatches = name.matches(place.name)
        val addressMatches = address.matchesAddress(place.address)
        if (place.name.normalized().length <= SHORT_PLACE_NAME_LENGTH) return addressMatches

        return addressMatches || (nameMatches && distanceMeters(place) <= MAX_DISTANCE_METERS)
    }

    private fun storePhotos(
        requests: List<PlaceThumbnailProvider.Request>,
        matches: List<GoogleMapsPlace?>,
    ): List<PlaceSupplement?> = Executors.newFixedThreadPool(properties.storageConcurrency).use { executor ->
        val storedPhotoTasks = requests.zip(matches).map { (request, match) ->
            match?.imageUrls.orEmpty().distinct().take(MAX_PHOTO_COUNT).mapIndexed { sequence, url ->
                executor.submit<String?> {
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
            }
        }
        matches.zip(storedPhotoTasks).map { (match, tasks) ->
            match?.let {
                PlaceSupplement(
                    openingHours = null,
                    photoUrls = tasks.mapNotNull { task -> task.get() },
                    googlePlaceId = match.placeId,
                )
            }
        }
    }

    private fun PlaceCandidate.actorQuery(): String = googlePlaceId?.let { "place_id:$it" } ?: "$name $address"

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
        val category: String?,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val imageUrls: List<String>,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(ApifyGoogleMapsPhotoProvider::class.java)
        const val AUTHORIZATION = "Authorization"
        const val PROVIDER = "APIFY_GOOGLE_MAPS"
        const val SOURCE_TYPE = "GOOGLE_MAPS_PHOTO"
        const val MAX_SEARCH_RESULT_COUNT = 5
        const val ACTOR_STAGE = "apify-actor"
        const val IMAGE_STORE_STAGE = "image-store"
        const val MAX_PHOTO_COUNT = 6
        const val MAX_DISTANCE_METERS = 300.0
        const val SHORT_PLACE_NAME_LENGTH = 1
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

private enum class PlaceCategoryGroup {
    FOOD,
    LODGING,
}

private fun String.matches(other: String): Boolean {
    val left = normalized()
    val right = other.normalized()
    if (left.isEmpty() || right.isEmpty()) return false
    return left == right || left.contains(right) || right.contains(left)
}

private fun String.matchesAddress(other: String): Boolean = matches(other) || roadAddressKey() == other.roadAddressKey()

private fun String.roadAddressKey(): String? {
    val tokens = split(Regex("\\s+")).map { it.normalized() }.filter(String::isNotEmpty)
    val roadNameIndex = tokens.indexOfLast { token -> ROAD_NAME_SUFFIXES.any(token::endsWith) }
    if (roadNameIndex >= 0) {
        val buildingNumber = tokens.drop(roadNameIndex + 1).firstOrNull { token -> token.all(Char::isDigit) }
        if (buildingNumber != null) return tokens[roadNameIndex] + buildingNumber
    }
    if (tokens.size < ROAD_ADDRESS_TOKEN_COUNT) return null
    return tokens.takeLast(ROAD_ADDRESS_TOKEN_COUNT).joinToString("")
}

private fun String.normalized(): String = lowercase().filter(Char::isLetterOrDigit)

private fun String?.conflictsWith(other: String?): Boolean {
    val left = toPlaceCategoryGroup()
    val right = other.toPlaceCategoryGroup()
    return left != null && right != null && left != right
}

private fun String?.toPlaceCategoryGroup(): PlaceCategoryGroup? {
    val value = this?.lowercase() ?: return null
    return when {
        LODGING_CATEGORY_KEYWORDS.any(value::contains) -> PlaceCategoryGroup.LODGING
        FOOD_CATEGORY_KEYWORDS.any(value::contains) -> PlaceCategoryGroup.FOOD
        else -> null
    }
}

private const val ROAD_ADDRESS_TOKEN_COUNT = 2
private val ROAD_NAME_SUFFIXES = setOf("로", "길")
private val FOOD_CATEGORY_KEYWORDS = setOf(
    "음식",
    "식당",
    "맛집",
    "카페",
    "베이커리",
    "빵",
    "술집",
    "주점",
    "restaurant",
    "cafe",
    "bakery",
    "bar",
)
private val LODGING_CATEGORY_KEYWORDS = setOf(
    "숙박",
    "숙소",
    "호텔",
    "모텔",
    "펜션",
    "hotel",
    "motel",
    "lodging",
)

private fun <T> measureThumbnailStage(
    metrics: ProcessingMetrics,
    clock: Clock,
    requests: List<PlaceThumbnailProvider.Request>,
    stage: String,
    action: () -> T,
): T {
    val postId = requests.firstNotNullOfOrNull(PlaceThumbnailProvider.Request::sourcePostId)
        ?: return action()
    return metrics.measure("place-thumbnail", stage, postId, null, clock, action)
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
