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

class ApifyNaverPlacePhotoProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: ApifyNaverPlacePhotoProperties,
    private val mediaStorage: PostMediaStoragePort,
    private val responseCache: ScrapingProviderResponseCache? = null,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? = fetchAll(listOf(request)).single()

    override fun fetchAll(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> {
        if (requests.isEmpty()) return emptyList()
        if (properties.apiToken.isBlank()) {
            logger.warn("Apify Naver place photo skipped: reason=missing_api_token")
            return List(requests.size) { null }
        }
        return requests.chunked(properties.batchSize).flatMap(::fetchBatch)
    }

    private fun fetchBatch(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> {
        val searchInput = mapOf(
            "keywords" to requests.map { it.place.searchQuery() },
            "scrapePlaceDetails" to false,
            "maxResultsPerKeyword" to properties.maxResults,
        )
        val searchResponse = measureNaverThumbnailStage(metrics, clock, requests, SEARCH_STAGE) {
            runActor(properties.searchActorId, searchInput)
        }
        searchResponse?.let { responseCache?.save(PROVIDER, SEARCH_SOURCE_TYPE, searchInput.hashCode().toString(), it) }
        val searchItems = parseSearchItems(searchResponse)
        val matches = requests.map { request -> searchItems.bestMatch(request.place) }
        val matchedUrls = matches.mapNotNull { it?.naverMapUrl }.distinct()
        if (matchedUrls.isEmpty()) return List(requests.size) { null }

        val photoInput = mapOf(
            "placeUrls" to matchedUrls.map { mapOf("url" to it) },
            "maxPhotos" to MAX_PHOTO_COUNT,
            "filterBy" to BUSINESS_FILTER,
            "includeFilters" to false,
        )
        val photoResponse = measureNaverThumbnailStage(metrics, clock, requests, PHOTO_STAGE) {
            runActor(properties.photoActorId, photoInput)
        }
        photoResponse?.let { responseCache?.save(PROVIDER, PHOTO_SOURCE_TYPE, photoInput.hashCode().toString(), it) }
        val photosByPlaceId = parsePhotos(photoResponse).groupBy(NaverPhoto::placeId)
        return measureNaverThumbnailStage(metrics, clock, requests, IMAGE_STORE_STAGE) {
            storePhotos(requests, matches, photosByPlaceId)
        }
    }

    private fun runActor(actorId: String, input: Map<String, Any>): String? = restClient.post()
        .uri("/v2/acts/{actorId}/run-sync-get-dataset-items?format=json&clean=true", actorId)
        .contentType(MediaType.APPLICATION_JSON)
        .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
        .body(input)
        .retrieve()
        .body(String::class.java)

    private fun parseSearchItems(body: String?): List<NaverPlaceMatch> = arrayItems(body).mapNotNull { node ->
        val name = node.textValue("Name", "name") ?: return@mapNotNull null
        val address = node.textValue("FullAddress", "Address", "fullAddress", "address") ?: return@mapNotNull null
        val placeId = node.textValue("PlaceId", "placeId") ?: return@mapNotNull null
        NaverPlaceMatch(
            placeId = placeId,
            name = name,
            address = address,
            latitude = node.decimalValue("Latitude", "latitude"),
            longitude = node.decimalValue("Longitude", "longitude"),
            naverMapUrl = node.textValue("NaverMapUrl", "naverMapUrl"),
            searchKeyword = node.textValue("SearchKeyword", "searchKeyword"),
        )
    }

    private fun parsePhotos(body: String?): List<NaverPhoto> = arrayItems(body).mapNotNull { node ->
        val placeId = node.textValue("placeId") ?: return@mapNotNull null
        val photoType = node.textValue("photoType") ?: return@mapNotNull null
        val originalUrl = node.textValue("originalUrl") ?: return@mapNotNull null
        originalUrl.takeIf(::isHttpUrl)?.let { NaverPhoto(placeId, photoType, it) }
    }.filter { it.photoType in BUSINESS_PHOTO_TYPES }

    private fun arrayItems(body: String?): List<JsonNode> = body?.let(objectMapper::readTree)
        ?.takeIf(JsonNode::isArray)?.toList().orEmpty()

    private fun List<NaverPlaceMatch>.bestMatch(place: PlaceCandidate): NaverPlaceMatch? {
        val query = place.searchQuery()
        val queryResults = filter { it.searchKeyword == query }.ifEmpty { this }
        return queryResults.filter { it.matches(place) }
            .minByOrNull { it.distanceMeters(place) ?: Double.MAX_VALUE }
    }

    private fun storePhotos(
        requests: List<PlaceThumbnailProvider.Request>,
        matches: List<NaverPlaceMatch?>,
        photosByPlaceId: Map<String, List<NaverPhoto>>,
    ): List<PlaceSupplement?> = Executors.newFixedThreadPool(properties.storageConcurrency).use { executor ->
        val tasksByRequest = requests.zip(matches).map { (request, match) ->
            photosByPlaceId[match?.placeId].orEmpty().map(NaverPhoto::originalUrl).distinct()
                .take(MAX_PHOTO_COUNT).mapIndexed { sequence, url ->
                    executor.submit<String?> {
                        runCatching {
                            mediaStorage.store(PostMedia(PostMedia.MediaType.IMAGE, url, sequence)).url
                        }.onFailure { exception ->
                            logger.warn(
                                "Apify Naver place photo storage failed: externalPlaceId={}, sequence={}",
                                request.place.externalPlaceId,
                                sequence,
                                exception,
                            )
                        }.getOrNull()
                    }
                }
        }
        tasksByRequest.map { tasks ->
            tasks.mapNotNull { it.get() }.takeIf(List<String>::isNotEmpty)?.let { storedUrls ->
                PlaceSupplement(openingHours = null, photoUrls = storedUrls)
            }
        }
    }

    private fun PlaceCandidate.searchQuery(): String = "$name $address"

    private data class NaverPlaceMatch(
        val placeId: String,
        val name: String,
        val address: String,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val naverMapUrl: String?,
        val searchKeyword: String?,
    ) {
        fun matches(place: PlaceCandidate): Boolean {
            if (!name.matchesName(place.name)) return false
            val nearby = distanceMeters(place)?.let { distance ->
                distance <= MAX_MATCH_DISTANCE_METERS
            } == true
            return address.matchesAddress(place.address) || nearby
        }

        fun distanceMeters(place: PlaceCandidate): Double? {
            val targetLatitude = latitude ?: return null
            val targetLongitude = longitude ?: return null
            return distanceMeters(place.latitude, place.longitude, targetLatitude, targetLongitude)
        }
    }

    private data class NaverPhoto(val placeId: String, val photoType: String, val originalUrl: String)

    private companion object {
        val logger = LoggerFactory.getLogger(ApifyNaverPlacePhotoProvider::class.java)
        const val AUTHORIZATION = "Authorization"
        const val PROVIDER = "APIFY_NAVER_PLACE"
        const val SEARCH_SOURCE_TYPE = "NAVER_PLACE_SEARCH"
        const val PHOTO_SOURCE_TYPE = "NAVER_PLACE_PHOTO"
        const val SEARCH_STAGE = "apify-naver-search"
        const val PHOTO_STAGE = "apify-naver-photo"
        const val IMAGE_STORE_STAGE = "image-store"
        const val BUSINESS_FILTER = "business"
        val BUSINESS_PHOTO_TYPES = setOf("ibu", "business")
        const val MAX_PHOTO_COUNT = 3
        const val MAX_MATCH_DISTANCE_METERS = 300.0
        const val EARTH_RADIUS_METERS = 6_371_000.0

        fun distanceMeters(lat1: BigDecimal, lon1: BigDecimal, lat2: BigDecimal, lon2: BigDecimal): Double {
            val latitudeDelta = Math.toRadians(lat2.toDouble() - lat1.toDouble())
            val longitudeDelta = Math.toRadians(lon2.toDouble() - lon1.toDouble())
            val firstLatitude = Math.toRadians(lat1.toDouble())
            val secondLatitude = Math.toRadians(lat2.toDouble())
            val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
                cos(firstLatitude) * cos(secondLatitude) * sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
            return EARTH_RADIUS_METERS * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
        }
    }
}

private fun String.matchesName(other: String): Boolean {
    val left = normalizedNaverValue()
    val right = other.normalizedNaverValue()
    return left.isNotEmpty() && right.isNotEmpty() && (left == right || left.contains(right) || right.contains(left))
}

private fun String.matchesAddress(other: String): Boolean {
    val left = normalizedNaverValue()
    val right = other.normalizedNaverValue()
    val normalizedMatch = left.isNotEmpty() && right.isNotEmpty() &&
        (left.contains(right) || right.contains(left))
    val roadMatch = roadAddressKey()?.let { it == other.roadAddressKey() } == true
    val districtMatch = districtKey()?.let { it == other.districtKey() } == true
    return normalizedMatch || (roadMatch && districtMatch)
}

private fun String.roadAddressKey(): String? {
    val tokens = split(Regex("\\s+")).map(String::normalizedNaverValue).filter(String::isNotEmpty)
    return tokens.takeLast(2).takeIf { it.size == 2 }?.joinToString("")
}

private fun String.districtKey(): String? = split(Regex("\\s+"))
    .map(String::normalizedNaverValue)
    .firstOrNull { token -> DISTRICT_SUFFIXES.any(token::endsWith) }

private val DISTRICT_SUFFIXES = setOf("구", "군")

private fun String.normalizedNaverValue(): String = lowercase().filter(Char::isLetterOrDigit)

private fun JsonNode.textValue(vararg names: String): String? = names.asSequence().map(::path)
    .firstOrNull { !it.isMissingNode && !it.isNull }?.asString()?.trim()?.takeIf(String::isNotEmpty)

private fun JsonNode.decimalValue(vararg names: String): BigDecimal? = textValue(*names)?.toBigDecimalOrNull()

private fun isHttpUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")

private fun <T> measureNaverThumbnailStage(
    metrics: ProcessingMetrics,
    clock: Clock,
    requests: List<PlaceThumbnailProvider.Request>,
    stage: String,
    action: () -> T,
): T {
    val postId = requests.firstNotNullOfOrNull(PlaceThumbnailProvider.Request::sourcePostId) ?: return action()
    return metrics.measure("place-thumbnail", stage, postId, null, clock, action)
}
