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
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? {
        if (properties.apiToken.isBlank()) {
            logger.warn("Apify Naver place skipped: reason=missing_api_token")
            return null
        }
        val response = restClient.post()
            .uri("/v2/acts/{actorId}/run-sync-get-dataset-items?format=json&clean=true", properties.actorId)
            .contentType(MediaType.APPLICATION_JSON)
            .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
            .body(
                mapOf(
                    "keywords" to listOf(request.place.searchQuery()),
                    "scrapePlaceDetails" to false,
                    "maxResultsPerKeyword" to properties.maxResults,
                ),
            )
            .retrieve()
            .body(String::class.java)
        val matched = parseItems(response).filter { it.matches(request.place) }
            .minByOrNull { it.distanceMeters(request.place) ?: Double.MAX_VALUE }
            ?: return null
        val storedUrls = matched.imageUrls.take(MAX_PHOTO_COUNT).mapIndexedNotNull { sequence, url ->
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
            )
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
        }.filter { it.isHttpUrl() }.distinct()
    }

    private fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")

    private fun PlaceCandidate.searchQuery(): String = listOf(name, address).joinToString(" ")

    private data class ApifyPlace(
        val name: String,
        val address: String,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val imageUrls: List<String>,
    ) {
        fun matches(place: PlaceCandidate): Boolean {
            val nameMatches = name.normalize().let { candidateName ->
                val placeName = place.name.normalize()
                candidateName == placeName || candidateName.contains(placeName) || placeName.contains(candidateName)
            }
            if (!nameMatches) return false
            val addressMatches = address.normalize().let { candidateAddress ->
                val placeAddress = place.address.normalize()
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

        private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ApifyNaverPlaceThumbnailProvider::class.java)
        const val AUTHORIZATION = "Authorization"
        const val MAX_PHOTO_COUNT = 3
        const val MAX_MATCH_DISTANCE_METERS = 300.0
        const val EARTH_RADIUS_METERS = 6_371_000.0

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
