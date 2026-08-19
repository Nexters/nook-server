package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
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

class ApifyNaverPlaceThumbnailProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: ApifyNaverPlaceProperties,
    private val mediaStorage: PostMediaStoragePort,
) : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? = fetchAll(listOf(request)).single()

    override fun fetchAll(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> {
        if (requests.isEmpty()) return emptyList()
        if (properties.apiToken.isBlank()) {
            logger.warn("Apify Naver place skipped: reason=missing_api_token")
            return List(requests.size) { null }
        }
        return requests.chunked(properties.batchSize).flatMap(::fetchBatch)
    }

    private fun fetchBatch(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> {
        val searchItems = runActor(
            mapOf(
                "keywords" to requests.map { it.place.searchQuery() },
                "scrapePlaceDetails" to false,
                "maxResultsPerKeyword" to properties.maxResults,
            ),
        )
        val matches = requests.map { request ->
            searchItems.forQuery(request.place.searchQuery())
                .filter { it.matches(request.place) }
                .minByOrNull { it.distanceMeters(request.place) ?: Double.MAX_VALUE }
        }
        val detailUrls = matches.mapNotNull { it?.naverMapUrl }.distinct()
        if (detailUrls.isEmpty()) return List(requests.size) { null }
        val detailItems = runActor(
            mapOf(
                "urls" to detailUrls,
                "scrapePlaceDetails" to true,
            ),
        )
        return requests.zip(matches).map { (request, match) ->
            val detail = match?.let { matched -> detailItems.matching(matched) } ?: return@map null
            storePhotos(request, detail.imageUrls)
        }
    }

    private fun runActor(input: Map<String, Any>): List<ApifyPlace> {
        val response = restClient.post()
            .uri("/v2/acts/{actorId}/run-sync-get-dataset-items?format=json&clean=true", properties.actorId)
            .contentType(MediaType.APPLICATION_JSON)
            .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
            .body(input)
            .retrieve()
            .body(String::class.java)
        return parseItems(response)
    }

    private fun storePhotos(request: PlaceThumbnailProvider.Request, imageUrls: List<String>): PlaceSupplement? {
        val storedUrls = imageUrls.take(MAX_PHOTO_COUNT).mapIndexedNotNull { sequence, url ->
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
        return storedUrls.takeIf(List<String>::isNotEmpty)?.let {
            PlaceSupplement(openingHours = null, photoUrls = it)
        }
    }

    private fun List<ApifyPlace>.forQuery(query: String): List<ApifyPlace> =
        filter { it.searchKeyword == query }.ifEmpty { this }

    private fun List<ApifyPlace>.matching(match: ApifyPlace): ApifyPlace? = firstOrNull { detail ->
        when {
            match.placeId != null && detail.placeId != null -> match.placeId == detail.placeId

            match.naverMapUrl != null && detail.naverMapUrl != null -> match.naverMapUrl == detail.naverMapUrl

            else -> normalize(detail.name) == normalize(match.name) &&
                normalize(detail.address).contains(normalize(match.address))
        }
    }

    private fun parseItems(body: String?): List<ApifyPlace> {
        val root = body?.let(objectMapper::readTree) ?: return emptyList()
        if (!root.isArray) return emptyList()
        return root.mapNotNull { node ->
            val name = node.text("Name", "name") ?: return@mapNotNull null
            val address = node.text("FullAddress", "Address", "fullAddress", "address") ?: return@mapNotNull null
            ApifyPlace(
                name = name,
                address = address,
                latitude = node.decimal("Latitude", "latitude"),
                longitude = node.decimal("Longitude", "longitude"),
                imageUrls = node.images("Images", "images", "imageUrls"),
                placeId = node.text("PlaceId", "placeId"),
                naverMapUrl = node.text("NaverMapUrl", "naverMapUrl"),
                searchKeyword = node.text("SearchKeyword", "searchKeyword"),
            )
        }
    }

    private fun PlaceCandidate.searchQuery(): String = listOf(name, address).joinToString(" ")

    private data class ApifyPlace(
        val name: String,
        val address: String,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val imageUrls: List<String>,
        val placeId: String?,
        val naverMapUrl: String?,
        val searchKeyword: String?,
    ) {
        fun matches(place: PlaceCandidate): Boolean {
            val nameMatches = normalize(name).let { candidateName ->
                val placeName = normalize(place.name)
                candidateName == placeName || candidateName.contains(placeName) || placeName.contains(candidateName)
            }
            if (!nameMatches) return false
            val addressMatches = normalize(address).let { candidateAddress ->
                val placeAddress = normalize(place.address)
                candidateAddress.contains(placeAddress) || placeAddress.contains(candidateAddress)
            }
            val nearby = distanceMeters(place)?.let { it <= MAX_MATCH_DISTANCE_METERS } ?: false
            return addressMatches || nearby
        }

        fun distanceMeters(place: PlaceCandidate): Double? {
            val candidateLatitude = latitude ?: return null
            val candidateLongitude = longitude ?: return null
            return distanceMeters(place.latitude, place.longitude, candidateLatitude, candidateLongitude)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ApifyNaverPlaceThumbnailProvider::class.java)
        const val AUTHORIZATION = "Authorization"
        const val MAX_PHOTO_COUNT = 6
        const val MAX_MATCH_DISTANCE_METERS = 300.0
        const val EARTH_RADIUS_METERS = 6_371_000.0

        fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

        fun distanceMeters(lat1: BigDecimal, lon1: BigDecimal, lat2: BigDecimal, lon2: BigDecimal): Double {
            val latitudeDelta = Math.toRadians(lat2.toDouble() - lat1.toDouble())
            val longitudeDelta = Math.toRadians(lon2.toDouble() - lon1.toDouble())
            val firstLatitude = Math.toRadians(lat1.toDouble())
            val secondLatitude = Math.toRadians(lat2.toDouble())
            val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
                cos(firstLatitude) * cos(secondLatitude) *
                sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
            return EARTH_RADIUS_METERS * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
        }
    }
}

private fun JsonNode.text(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
    get(name)?.takeUnless(JsonNode::isNull)?.asText()?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonNode.decimal(vararg names: String): BigDecimal? = text(*names)?.toBigDecimalOrNull()

private fun JsonNode.images(vararg names: String): List<String> {
    val images = names.firstNotNullOfOrNull { get(it)?.takeIf(JsonNode::isArray) } ?: return emptyList()
    return images.mapNotNull { image ->
        if (image.isTextual) image.asText() else image.text("url", "Url", "originalUrl", "imageUrl")
    }.filter { it.startsWith("https://") || it.startsWith("http://") }.distinct()
}
