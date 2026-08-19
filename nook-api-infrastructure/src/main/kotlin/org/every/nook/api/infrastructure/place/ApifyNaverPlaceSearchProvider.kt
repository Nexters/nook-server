package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class ApifyNaverPlaceSearchProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: ApifyNaverPlaceProperties,
    private val responseCache: ScrapingProviderResponseCache? = null,
) : PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        if (properties.apiToken.isBlank()) return emptyList()
        val body = restClient.post()
            .uri("/v2/acts/{actorId}/run-sync-get-dataset-items?format=json&clean=true", properties.actorId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${properties.apiToken}")
            .body(
                mapOf(
                    "keywords" to listOf(request.query),
                    "scrapePlaceDetails" to true,
                    "maxResultsPerKeyword" to minOf(request.size, properties.maxResults),
                ),
            )
            .retrieve()
            .body(String::class.java)
            .orEmpty()
        responseCache?.save(PROVIDER, SOURCE_TYPE, request.query, body)
        val root = objectMapper.readTree(body)
        if (!root.isArray) return emptyList()
        return root.mapNotNull(::candidate)
    }

    private fun candidate(node: JsonNode): PlaceCandidate? = runCatching {
        PlaceCandidate(
            provider = "NAVER",
            externalPlaceId = requireNotNull(node.text("PlaceId", "placeId")),
            name = requireNotNull(node.text("Name", "name")),
            address = requireNotNull(node.text("FullAddress", "Address", "fullAddress", "address")),
            latitude = requireNotNull(node.text("Latitude", "latitude")?.toBigDecimalOrNull()),
            longitude = requireNotNull(node.text("Longitude", "longitude")?.toBigDecimalOrNull()),
            category = node.text("Category", "category"),
            phoneNumber = node.text("Contact", "contact"),
            providerUrl = node.text("NaverMapUrl", "naverMapUrl"),
        )
    }.getOrNull()

    private fun JsonNode.text(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        get(name)?.takeUnless(JsonNode::isNull)?.asText()?.trim()?.takeIf(String::isNotEmpty)
    }

    private companion object {
        const val PROVIDER = "APIFY_NAVER"
        const val SOURCE_TYPE = "NAVER_PLACE_SEARCH"
    }
}
