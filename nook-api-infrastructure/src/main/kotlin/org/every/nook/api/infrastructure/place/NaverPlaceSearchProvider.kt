package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.billing.NoOpExternalApiUsageMeter
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.every.nook.api.application.place.PlaceSearchProviderTimeoutException
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.infrastructure.billing.ExternalApiCallMeter
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.SocketTimeoutException

class NaverPlaceSearchProvider(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: NaverPlaceProperties,
    private val mapper: NaverPlaceMapper,
    private val callMeter: ExternalApiCallMeter = ExternalApiCallMeter(NoOpExternalApiUsageMeter),
) : PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        val startedAt = System.nanoTime()
        ensureConfigured()
        val responseBody = try {
            callMeter.measure("naver-local", "local-search", "place-search") {
                restClient.get()
                    .uri { builder ->
                        builder.path(SEARCH_PATH)
                            .queryParam(QUERY, request.query)
                            .queryParam(DISPLAY, request.size.coerceIn(1, MAX_DISPLAY))
                            .queryParam(START, 1)
                            .build()
                    }
                    .header(CLIENT_ID, properties.clientId)
                    .header(CLIENT_SECRET, properties.clientSecret)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String::class.java)
            }
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
                    eventLogger.info(
                        ProcessingLogEvent(
                            action = "place.provider.search.completed",
                            flow = "place",
                            stage = "search",
                            outcome = "success",
                            durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND,
                            fields = mapOf(
                                "provider.name" to "naver",
                                "provider.result_count" to candidates.size,
                            ),
                        ),
                    )
                    logger.info {
                        "Naver local search completed: query=${request.query}, candidateCount=${candidates.size}"
                    }
                }
        }.getOrElse { exception ->
            logger.warn(exception) { "Failed to map Naver local search response" }
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
        val eventLogger = LoggerFactory.getLogger(NaverPlaceSearchProvider::class.java)
        const val NANOS_PER_MILLISECOND = 1_000_000

        const val SEARCH_PATH = "/search/v1/local"
        const val QUERY = "query"
        const val DISPLAY = "display"
        const val START = "start"
        const val MAX_DISPLAY = 5
        const val CLIENT_ID = "X-NCP-APIGW-API-KEY-ID"
        const val CLIENT_SECRET = "X-NCP-APIGW-API-KEY"
    }
}
