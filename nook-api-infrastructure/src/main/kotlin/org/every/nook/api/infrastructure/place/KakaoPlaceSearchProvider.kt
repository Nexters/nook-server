package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.place.PagedPlaceSearchProvider
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceCandidatePage
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.every.nook.api.application.place.PlaceSearchProviderTimeoutException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.SocketTimeoutException

class KakaoPlaceSearchProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: KakaoPlaceProperties,
    private val mapper: KakaoPlaceMapper,
) : PlaceSearchProvider,
    PagedPlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> =
        searchPage(request.copy(page = FIRST_PAGE, size = DEFAULT_RESULT_SIZE)).items

    override fun searchPage(request: PlaceSearchProvider.Request): PlaceCandidatePage {
        ensureConfigured()
        val responseBody = try {
            restClient.get()
                .uri { builder ->
                    builder.path(SEARCH_PATH)
                        .queryParam(QUERY, request.query)
                        .queryParam(PAGE, request.page)
                        .queryParam(SIZE, request.size)
                    request.longitude?.let { builder.queryParam(LONGITUDE, it) }
                    request.latitude?.let { builder.queryParam(LATITUDE, it) }
                    request.radius?.let { builder.queryParam(RADIUS, it) }
                    builder.build()
                }
                .header(AUTHORIZATION, "$AUTHORIZATION_PREFIX${properties.restApiKey}")
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
            val response = objectMapper.readValue(responseBody, KakaoPlaceResponse::class.java)
            PlaceCandidatePage(
                items = mapper.map(response),
                page = request.page,
                size = request.size,
                hasNext = !response.meta.isEnd,
            ).also { page ->
                logger.info {
                    "Kakao place search completed: query=${request.query}, page=${request.page}, " +
                        "candidateCount=${page.items.size}"
                }
            }
        }.getOrElse { exception ->
            logger.warn("Failed to map Kakao Local place response", exception)
            providerFailure(exception)
        }
    }

    private fun ensureConfigured() {
        if (properties.restApiKey.isBlank()) {
            providerFailure()
        }
    }

    private fun Throwable.hasSocketTimeoutCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

    private fun providerTimeout(cause: Throwable? = null): Nothing = throw PlaceSearchProviderTimeoutException(cause)

    private fun providerFailure(cause: Throwable? = null): Nothing = throw PlaceSearchProviderException(cause)

    private companion object {
        val logger = KotlinLogging.logger {}

        const val SEARCH_PATH = "/v2/local/search/keyword.json"
        const val QUERY = "query"
        const val PAGE = "page"
        const val SIZE = "size"
        const val LONGITUDE = "x"
        const val LATITUDE = "y"
        const val RADIUS = "radius"
        const val FIRST_PAGE = 1
        const val DEFAULT_RESULT_SIZE = 15
        const val AUTHORIZATION = "Authorization"
        const val AUTHORIZATION_PREFIX = "KakaoAK "
    }
}
