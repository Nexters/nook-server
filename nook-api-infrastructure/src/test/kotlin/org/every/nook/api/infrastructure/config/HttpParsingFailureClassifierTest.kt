package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.processing.ParsingFailureKind
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClientResponseException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpParsingFailureClassifierTest {
    private val classifier = HttpParsingFailureClassifier(
        Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `classifies wrapped provider errors without exposing response body`() {
        listOf(400, 404, 410, 422).forEach { status ->
            assertEquals(ParsingFailureKind.PERMANENT, classifier.classify(response(status)).kind)
        }
        listOf(401, 403).forEach { status ->
            assertEquals(ParsingFailureKind.CONFIGURATION, classifier.classify(response(status)).kind)
        }
        assertEquals(ParsingFailureKind.TRANSIENT, classifier.classify(IllegalStateException(response(503))).kind)
        assertEquals(ParsingFailureKind.TRANSIENT, classifier.classify(IOException("network")).kind)
        assertEquals(ParsingFailureKind.UNKNOWN, classifier.classify(IllegalStateException("unknown")).kind)
    }

    @Test
    fun `rate limit accepts seconds date and bounds while ignoring malformed headers`() {
        assertEquals(Duration.ofSeconds(120), classifier.classify(response(429, "120")).retryAfter)
        assertEquals(
            Duration.ofSeconds(120),
            classifier.classify(response(429, "Sun, 13 Sep 2026 00:02:00 GMT")).retryAfter,
        )
        assertEquals(Duration.ofMinutes(30), classifier.classify(response(429, "9999999")).retryAfter)
        assertEquals(Duration.ZERO, classifier.classify(response(429, "-1")).retryAfter)
        assertEquals(null, classifier.classify(response(429, "invalid")).retryAfter)
        assertEquals(ParsingFailureKind.RATE_LIMIT, classifier.classify(response(429)).kind)
    }

    private fun response(status: Int, retryAfter: String? = null): RestClientResponseException {
        val headers = HttpHeaders().apply { retryAfter?.let { set("Retry-After", it) } }
        return RestClientResponseException("provider error", status, "error", headers, byteArrayOf(), null)
    }
}
