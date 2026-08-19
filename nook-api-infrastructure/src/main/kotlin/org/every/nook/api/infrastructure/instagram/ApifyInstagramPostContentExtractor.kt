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

class ApifyInstagramPostContentExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: ApifyProperties,
    private val mapper: ApifyInstagramMapper,
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
            callMeter.measure("apify", "actor-run", "instagram-scraping") {
                restClient.post()
                    .uri { builder ->
                        builder.path(RUN_SYNC_PATH)
                            .queryParam(FORMAT, JSON_FORMAT)
                            .queryParam(CLEAN, true)
                            .build(properties.actorId)
                    }
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AUTHORIZATION, "Bearer ${properties.apiToken}")
                    .body(
                        mapOf(
                            DIRECT_URLS to listOf(instagramUrl.canonicalUrl),
                            RESULTS_TYPE to resultsType(instagramUrl),
                            RESULTS_LIMIT to 1,
                        ),
                    )
                    .retrieve()
                    .body(String::class.java)
            }
        } catch (exception: RestClientResponseException) {
            logger.logProviderRequestFailed(PROVIDER, startedAt, exception, exception.statusCode.value())
            if (exception.statusCode.value() in TIMEOUT_STATUSES) {
                providerTimeout(exception)
            }
            providerFailure(exception)
        } catch (exception: ResourceAccessException) {
            logger.logProviderRequestFailed(PROVIDER, startedAt, exception)
            handleResourceAccessException(exception)
        }

        val extracted = mapResponse(instagramUrl, parseResponse(responseBody))
        responseBody?.let { body -> responseCache.save(PROVIDER, SOURCE_TYPE, instagramUrl.shortcode, body) }
        logger.logProviderRequestCompleted(PROVIDER, startedAt, extracted.post.media.size)
        return extracted
    }

    private fun parseResponse(responseBody: String?): JsonNode? = responseBody?.let { body ->
        runCatching { objectMapper.readTree(body) }.getOrElse(::providerFailure)
    }

    private fun mapResponse(url: InstagramContentUrl, root: JsonNode?): ExtractedPostContent {
        if (root == null || root.isNull || !root.isArray) {
            if (root == null || root.isNull) contentNotFound()
            providerFailure()
        }
        val first = root.firstOrNull() ?: contentNotFound()
        val record = runCatching { objectMapper.treeToValue(first, ApifyInstagramRecord::class.java) }
            .getOrElse {
                logger.warn("Failed to map Apify Instagram response", it)
                providerFailure(it)
            }
        if (!record.error.isNullOrBlank()) {
            if (record.isContentUnavailable()) contentNotFound()
            providerFailure()
        }
        return mapper.map(url, record)
    }

    private fun ApifyInstagramRecord.isContentUnavailable(): Boolean {
        val text = listOfNotNull(error, errorDescription).joinToString(" ").lowercase()
        return CONTENT_UNAVAILABLE_MARKERS.any(text::contains)
    }

    private fun resultsType(url: InstagramContentUrl): String = when (url.kind) {
        InstagramContentUrl.Kind.POST -> POSTS
        InstagramContentUrl.Kind.REEL -> REELS
    }

    private fun Throwable.hasSocketTimeoutCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

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
        val logger = LoggerFactory.getLogger(ApifyInstagramPostContentExtractor::class.java)

        const val RUN_SYNC_PATH = "/v2/acts/{actorId}/run-sync-get-dataset-items"
        const val AUTHORIZATION = "Authorization"
        const val FORMAT = "format"
        const val JSON_FORMAT = "json"
        const val CLEAN = "clean"
        const val DIRECT_URLS = "directUrls"
        const val RESULTS_TYPE = "resultsType"
        const val RESULTS_LIMIT = "resultsLimit"
        const val POSTS = "posts"
        const val REELS = "reels"
        const val PROVIDER = "APIFY"
        const val SOURCE_TYPE = "INSTAGRAM"
        val TIMEOUT_STATUSES = setOf(408, 504)
        val CONTENT_UNAVAILABLE_MARKERS = listOf(
            "no_items",
            "not_found",
            "not found",
            "does not exist",
            "private",
            "restricted",
            "empty",
        )
    }
}
