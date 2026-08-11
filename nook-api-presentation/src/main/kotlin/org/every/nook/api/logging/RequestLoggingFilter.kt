package org.every.nook.api.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.Locale
import java.util.UUID

@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter(
    private val properties: HttpLoggingProperties,
    private val bodyLogFieldExtractor: BodyLogFieldExtractor,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !properties.enabled || properties.ignoredPathPrefixes.any { request.requestURI.startsWith(it) }

    @Suppress("TooGenericExceptionCaught")
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        val requestId = resolveRequestId(request)
        val requestToUse = request.wrapForBodyCapture()
        val responseToUse = response.wrapForBodyCapture()
        var chainException: Throwable? = null

        response.setHeader(properties.requestIdHeader, requestId)
        putRequestContext(requestToUse, requestId)

        try {
            filterChain.doFilter(requestToUse, responseToUse)
        } catch (exception: Exception) {
            chainException = exception
            throw exception
        } finally {
            try {
                val durationMs = ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND)
                putResponseContext(requestToUse, responseToUse, durationMs, chainException)
                apiLogger.info(apiLogMessage(requestToUse, responseToUse, durationMs))
            } finally {
                (responseToUse as? ContentCachingResponseWrapper)?.copyBodyToResponse()
                MDC.clear()
            }
        }
    }

    private fun HttpServletRequest.wrapForBodyCapture(): HttpServletRequest =
        if (properties.body.requestEnabled && bodyLogFieldExtractor.hasIncludedContentType(contentType)) {
            ContentCachingRequestWrapper(this, properties.body.maxBytes)
        } else {
            this
        }

    private fun HttpServletResponse.wrapForBodyCapture(): HttpServletResponse =
        if (properties.body.responseEnabled) ContentCachingResponseWrapper(this) else this

    private fun putRequestContext(request: HttpServletRequest, requestId: String) {
        putMdc(RequestLoggingFields.REQUEST_ID, requestId)
        putMdc(RequestLoggingFields.REQUEST_METHOD, request.method)
        putMdc(RequestLoggingFields.REQUEST_PATH, request.requestURI)
        putMdc(RequestLoggingFields.REQUEST_QUERY, request.queryString)
        putMdc(RequestLoggingFields.REQUEST_URL, request.requestURL.toString())
        putMdc(RequestLoggingFields.REQUEST_CLIENT_IP, clientIp(request))
        putMdc(RequestLoggingFields.REQUEST_CONTENT_TYPE, request.contentType)
        putMdc(RequestLoggingFields.REQUEST_SIZE_BYTES, request.contentLengthLong.takeIf { it >= 0 })
        putMdc(RequestLoggingFields.HTTP_METHOD, request.method)

        properties.headers.included.forEach { header ->
            putMdc("request.headers.${header.toLogFieldName()}", request.getHeader(header))
        }
    }

    private fun putResponseContext(
        request: HttpServletRequest,
        response: HttpServletResponse,
        durationMs: Long,
        chainException: Throwable?,
    ) {
        val route = route(request) ?: request.requestURI
        val status = response.status

        putMdc(RequestLoggingFields.HTTP_ROUTE, route)
        putMdc(RequestLoggingFields.HTTP_STATUS_CODE, status)
        putMdc(RequestLoggingFields.TRANSACTION_NAME, "${request.method} $route")
        putMdc(RequestLoggingFields.TRANSACTION_TYPE, "request")
        putMdc(RequestLoggingFields.TRANSACTION_DURATION_MS, durationMs)
        putMdc(RequestLoggingFields.RESPONSE_STATUS, status)
        putMdc(RequestLoggingFields.RESPONSE_CONTENT_TYPE, response.contentType)
        putMdc(
            RequestLoggingFields.RESPONSE_SIZE_BYTES,
            (response as? ContentCachingResponseWrapper)?.contentSize?.takeIf { it >= 0 },
        )

        chainException?.let {
            putMdc(RequestLoggingFields.ERROR_TYPE, it::class.qualifiedName ?: it::class.simpleName)
            putMdc(RequestLoggingFields.ERROR_MESSAGE, it.message)
        }

        putBodyContext("request.body", request, properties.body.requestEnabled)
        putBodyContext("response.body", response, properties.body.responseEnabled)
    }

    private fun putBodyContext(prefix: String, message: Any, enabled: Boolean) {
        if (!enabled) {
            return
        }
        val request = message as? ContentCachingRequestWrapper
        val response = message as? ContentCachingResponseWrapper
        val fields = bodyLogFieldExtractor.extract(
            prefix = prefix,
            body = request?.contentAsByteArray ?: response?.contentAsByteArray,
            contentType = request?.contentType ?: response?.contentType,
            charset = request?.characterEncoding ?: response?.characterEncoding,
        )
        fields.forEach { (key, value) -> putMdc(key, value) }
    }

    private fun resolveRequestId(request: HttpServletRequest): String {
        val incoming = request.getHeader(properties.requestIdHeader)?.trim()
        return incoming
            ?.takeIf { id -> id.length in 1..MAX_REQUEST_ID_LENGTH }
            ?.takeIf { id -> id.all { it.isLetterOrDigit() || it in REQUEST_ID_ALLOWED_SYMBOLS } }
            ?: UUID.randomUUID().toString()
    }

    private fun clientIp(request: HttpServletRequest): String? = request.getHeader("X-Forwarded-For")
        ?.split(',')
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: request.getHeader("X-Real-IP")?.takeIf { it.isNotBlank() }
        ?: request.remoteAddr

    private fun route(request: HttpServletRequest): String? =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String

    private fun String.toLogFieldName(): String = lowercase(Locale.ROOT)
        .replace('-', '_')
        .replace(Regex("[^a-z0-9_.]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    private companion object {
        val apiLogger = LoggerFactory.getLogger(RequestLoggingFilter::class.java)
        const val MAX_REQUEST_ID_LENGTH = 128
        const val NANOS_PER_MILLISECOND = 1_000_000
        val REQUEST_ID_ALLOWED_SYMBOLS = setOf('-', '_', '.', ':')
    }
}

private fun putMdc(key: String, value: Any?) {
    value?.toString()?.takeIf { it.isNotBlank() }?.let { MDC.put(key, it) }
}

private fun apiLogMessage(request: HttpServletRequest, response: HttpServletResponse, durationMs: Long): String =
    "req: ${request.method} ${requestTarget(request)}\nres: ${response.status} ${durationMs}ms"

private fun requestTarget(request: HttpServletRequest): String =
    request.queryString?.takeIf { it.isNotBlank() }?.let { "${request.requestURI}?$it" } ?: request.requestURI
