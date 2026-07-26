package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException

class BrightDataInstagramPostContentExtractor(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: BrightDataProperties,
    private val mapper: BrightDataInstagramMapper,
) : PostContentExtractor {
    override fun supports(url: String): Boolean = InstagramContentUrl.supports(url)

    override fun extract(url: String): ExtractedPostContent {
        val instagramUrl = InstagramContentUrl.parse(url)
        if (properties.apiToken.isBlank()) {
            providerFailure()
        }
        val responseBody = try {
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
        } catch (exception: RestClientResponseException) {
            handleResponseException(exception)
        } catch (exception: ResourceAccessException) {
            handleResourceAccessException(exception)
        }

        return mapResponse(instagramUrl, parseResponse(responseBody))
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
    }
}
