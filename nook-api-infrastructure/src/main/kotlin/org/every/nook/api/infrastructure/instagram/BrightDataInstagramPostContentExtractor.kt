package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.billing.NoOpExternalApiUsageMeter
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.every.nook.api.infrastructure.billing.ExternalApiCallMeter
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.SocketTimeoutException

class BrightDataInstagramPostContentExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: BrightDataProperties,
    private val mapper: BrightDataInstagramMapper,
    private val responseCache: ScrapingProviderResponseCache,
    private val callMeter: ExternalApiCallMeter = ExternalApiCallMeter(NoOpExternalApiUsageMeter),
) : PostContentExtractor {
    override fun supports(url: String): Boolean = InstagramContentUrl.supports(url)

    override fun extract(url: String): ExtractedPostContent {
        val startedAt = System.nanoTime()
        val instagramUrl = InstagramContentUrl.parse(url)
        responseCache.find(PROVIDER, SOURCE_TYPE, instagramUrl.shortcode)?.let { cached ->
            logger.logCacheHit(PROVIDER, startedAt)
            return mapResponse(instagramUrl, parseResponse(cached))
        }
        if (properties.apiToken.isBlank()) {
            providerFailure()
        }
        val responseBody = try {
            logger.logProviderRequestStarted(PROVIDER)
            callMeter.measure("bright-data", "dataset-scrape", "instagram-scraping") {
                restClient.post()
                    .uri { builder ->
                        builder.path(SCRAPE_PATH)
                            .queryParam(DATASET_ID, datasetId(instagramUrl))
                            .queryParam(FORMAT, JSON_FORMAT)
                            .build()
                    }
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
                    .body(listOf(mapOf(URL to instagramUrl.canonicalUrl)))
                    .retrieve()
                    .body(String::class.java)
            }
        } catch (exception: RestClientResponseException) {
            logger.logProviderRequestFailed(PROVIDER, startedAt, exception, exception.statusCode.value())
            handleResponseException(exception)
        } catch (exception: ResourceAccessException) {
            logger.logProviderRequestFailed(PROVIDER, startedAt, exception)
            handleResourceAccessException(exception)
        }

        val extracted = mapResponse(instagramUrl, parseResponse(responseBody))
        responseBody?.let { body ->
            responseCache.save(PROVIDER, SOURCE_TYPE, instagramUrl.shortcode, body)
        }
        logger.logProviderRequestCompleted(PROVIDER, startedAt, extracted.post.media.size)
        return extracted
    }

    private fun parseResponse(responseBody: String?): JsonNode? = responseBody?.let { body ->
        runCatching { objectMapper.readTree(body) }
            .getOrElse(::providerFailure)
    }

    private fun mapResponse(url: InstagramContentUrl, root: JsonNode?): ExtractedPostContent {
        if (root == null || root.isNull) {
            contentNotFound()
        }
        if (root.isObject && root.has(SNAPSHOT_ID)) {
            providerTimeout()
        }
        if (!root.isArray) {
            providerFailure()
        }
        val first = root.firstOrNull() ?: contentNotFound()
        return runCatching {
            mapper.map(url, objectMapper.treeToValue(first, BrightDataInstagramRecord::class.java))
        }.getOrElse {
            logger.warn("Failed to map Bright Data Instagram response", it)
            providerFailure(it)
        }
    }

    private fun datasetId(url: InstagramContentUrl): String = when (url.kind) {
        InstagramContentUrl.Kind.POST -> properties.postsDatasetId
        InstagramContentUrl.Kind.REEL -> properties.reelsDatasetId
    }

    private fun Throwable.hasSocketTimeoutCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

    private fun handleResponseException(exception: RestClientResponseException): Nothing {
        if (exception.statusCode.value() == NOT_FOUND_STATUS) {
            contentNotFound()
        }
        providerFailure(exception)
    }

    private fun handleResourceAccessException(exception: ResourceAccessException): Nothing {
        if (exception.hasSocketTimeoutCause()) {
            providerTimeout(exception)
        }
        providerFailure(exception)
    }

    private fun contentNotFound(): Nothing = throw PostContentNotFoundException()

    private fun providerTimeout(cause: Throwable? = null): Nothing = throw PostContentProviderTimeoutException(cause)

    private fun providerFailure(cause: Throwable? = null): Nothing = throw PostContentProviderException(cause)

    private companion object {
        val logger = LoggerFactory.getLogger(BrightDataInstagramPostContentExtractor::class.java)

        const val SCRAPE_PATH = "/datasets/v3/scrape"
        const val DATASET_ID = "dataset_id"
        const val FORMAT = "format"
        const val JSON_FORMAT = "json"
        const val AUTHORIZATION = "Authorization"
        const val URL = "url"
        const val SNAPSHOT_ID = "snapshot_id"
        const val NOT_FOUND_STATUS = 404
        const val PROVIDER = "BRIGHT_DATA"
        const val SOURCE_TYPE = "INSTAGRAM"
    }
}
