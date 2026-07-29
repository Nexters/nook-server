package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.every.nook.api.application.place.PlaceSearchProviderTimeoutException
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException

class NaverPlaceSearchProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: NaverPlaceProperties,
    private val mapper: NaverPlaceMapper,
) : PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        ensureConfigured()
        val responseBody = try {
            restClient.get()
                .uri { builder ->
                    builder.path(SEARCH_PATH)
                        .queryParam(QUERY, request.query)
                        .build()
                }
                .header(CLIENT_ID, properties.clientId)
                .header(CLIENT_SECRET, properties.clientSecret)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
        } catch (exception: RestClientResponseException) {
            providerFailure(exception)
        } catch (exception: ResourceAccessException) {
            if (exception.hasSocketTimeoutCause()) {
                providerTimeout(exception)
            }
            providerFailure(exception)
        }

        return runCatching {
            mapper.map(request.query, objectMapper.readValue(responseBody, NaverPlaceResponse::class.java))
                .also { candidates ->
                    logger.info {
                        "Naver map geocoding completed: query=${request.query}, candidateCount=${candidates.size}"
                    }
                }
        }.getOrElse { exception ->
            logger.warn(exception) { "Failed to map Naver Map geocoding response" }
            providerFailure(exception)
        }
    }

    private fun ensureConfigured() {
        if (properties.clientId.isBlank() || properties.clientSecret.isBlank()) {
            providerFailure()
        }
    }

    private fun Throwable.hasSocketTimeoutCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

    private fun providerTimeout(cause: Throwable? = null): Nothing = throw PlaceSearchProviderTimeoutException(cause)

    private fun providerFailure(cause: Throwable? = null): Nothing = throw PlaceSearchProviderException(cause)

    private companion object {
        val logger = KotlinLogging.logger {}

        const val SEARCH_PATH = "/map-geocode/v2/geocode"
        const val QUERY = "query"
        const val CLIENT_ID = "X-NCP-APIGW-API-KEY-ID"
        const val CLIENT_SECRET = "X-NCP-APIGW-API-KEY"
    }
}
