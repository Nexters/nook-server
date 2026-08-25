package org.every.nook.api.infrastructure.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderUsageCommand
import org.every.nook.api.application.providerusage.ExternalProviderUsageRecorder
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import java.io.IOException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@Component
class ExternalProviderUsageInterceptorFactory(private val recorder: ExternalProviderUsageRecorder) {
    fun create(provider: String, runtime: String = API): ClientHttpRequestInterceptor =
        UsageInterceptor(provider, runtime, recorder)

    private companion object {
        const val API = "API"
    }
}

private class UsageInterceptor(
    private val provider: String,
    private val runtime: String,
    private val recorder: ExternalProviderUsageRecorder,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val startedAt = Instant.now()
        return try {
            execution.execute(request, body).also { response ->
                val status = if (response.statusCode.isError) FAILED else SUCCEEDED
                record(request, startedAt, response.statusCode.value(), status)
            }
        } catch (exception: IOException) {
            record(request, startedAt, null, FAILED, exception.javaClass.simpleName)
            throw exception
        }
    }

    private fun record(
        request: HttpRequest,
        startedAt: Instant,
        httpStatus: Int?,
        status: String,
        failureCode: String? = null,
    ) {
        val operation = operation(request)
        runCatching {
            recorder.record(
                ExternalProviderUsageCommand(
                    provider = provider,
                    operation = operation,
                    sku = sku(provider, operation, status),
                    unitType = if (provider == BRIGHT_DATA) RECORD else REQUEST,
                    units = BigDecimal.ONE,
                    status = status,
                    runtime = runtime,
                    durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                    httpStatus = httpStatus,
                    failureType = failureCode ?: httpStatus?.takeIf { it >= HTTP_ERROR_START }?.let { "HTTP_$it" },
                    occurredAt = startedAt,
                ),
            )
        }
    }

    private fun operation(request: HttpRequest): String {
        val path = request.uri.path.trim('/').replace('/', '_').replace(NON_OPERATION_CHARACTER, "_")
        return "${request.method}_${path.ifBlank { "root" }}".take(MAX_OPERATION_LENGTH)
    }

    private fun sku(provider: String, operation: String, status: String): String = when (provider) {
        OPENAI -> "GPT_5_NANO_REQUEST_UNPRICED"
        APIFY -> "INSTAGRAM_SCRAPER"
        APIFY_GOOGLE_MAPS -> "GOOGLE_MAPS_SCRAPER"
        APIFY_NAVER_PLACE -> apifyNaverSku(operation)
        GOOGLE_VISION -> "TEXT_DETECTION"
        GOOGLE_PLACES -> googlePlacesSku(operation)
        BRIGHT_DATA -> if (status == SUCCEEDED) "WEB_SCRAPER_SUCCESS_RECORD" else "REQUEST_UNPRICED"
        else -> "REQUEST_UNPRICED"
    }

    private fun apifyNaverSku(operation: String): String = when {
        operation.contains(NAVER_PHOTO_ACTOR, ignoreCase = true) -> "NAVER_PLACE_PHOTO_SCRAPER"
        operation.contains(NAVER_SEARCH_ACTOR, ignoreCase = true) -> "NAVER_MAP_SEARCH_RESULTS_SCRAPER"
        else -> "REQUEST_UNPRICED"
    }

    private fun googlePlacesSku(operation: String): String = when {
        operation.contains("searchNearby", ignoreCase = true) -> "NEARBY_SEARCH_PRO"
        operation.contains("searchText", ignoreCase = true) -> "TEXT_SEARCH_PRO"
        operation.contains("media", ignoreCase = true) -> "PLACE_DETAILS_PHOTOS"
        else -> "PLACE_DETAILS_PRO"
    }

    private companion object {
        const val HTTP_ERROR_START = 400
        const val MAX_OPERATION_LENGTH = 100
        const val FAILED = "FAILED"
        const val SUCCEEDED = "SUCCEEDED"
        const val OPENAI = "OPENAI"
        const val APIFY = "APIFY"
        const val APIFY_GOOGLE_MAPS = "APIFY_GOOGLE_MAPS"
        const val APIFY_NAVER_PLACE = "APIFY_NAVER_PLACE"
        const val NAVER_PHOTO_ACTOR = "naver-place-photos"
        const val NAVER_SEARCH_ACTOR = "naver-map-search-results-scraper"
        const val GOOGLE_VISION = "GOOGLE_VISION"
        const val GOOGLE_PLACES = "GOOGLE_PLACES"
        const val BRIGHT_DATA = "BRIGHT_DATA"
        const val RECORD = "RECORD"
        const val REQUEST = "REQUEST"
        val NON_OPERATION_CHARACTER = Regex("[^A-Za-z0-9_-]")
    }
}
