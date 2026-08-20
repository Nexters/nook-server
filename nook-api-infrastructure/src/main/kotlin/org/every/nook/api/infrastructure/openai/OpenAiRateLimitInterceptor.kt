package org.every.nook.api.infrastructure.openai

import mu.KotlinLogging
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadLocalRandom

internal class OpenAiRateLimitInterceptor(
    maxConcurrentRequests: Int,
    private val retryBackoffs: List<Duration>,
    private val sleep: (Duration) -> Unit = { duration -> Thread.sleep(duration.toMillis()) },
    private val jitter: (Duration) -> Duration = ::withJitter,
) : ClientHttpRequestInterceptor {
    private val permits = Semaphore(maxConcurrentRequests, true)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        permits.acquire()
        try {
            return executeWithRateLimitRetry(request, body, execution)
        } finally {
            permits.release()
        }
    }

    private fun executeWithRateLimitRetry(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        retryBackoffs.forEachIndexed { attempt, backoff ->
            val response = execution.execute(request, body)
            if (response.statusCode != HttpStatus.TOO_MANY_REQUESTS) {
                return response
            }

            val delay = response.headers.getFirst(RETRY_AFTER_HEADER)
                ?.toRetryAfterDuration()
                ?: jitter(backoff)
            response.close()
            logger.warn {
                "OpenAI rate limit received; retrying: attempt=${attempt + 1}, delayMs=${delay.toMillis()}"
            }
            sleep(delay)
        }
        return execution.execute(request, body)
    }

    private fun String.toRetryAfterDuration(): Duration? = trim().toLongOrNull()
        ?.let(Duration::ofSeconds)
        ?: runCatching {
            Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME))
                .coerceAtLeast(Duration.ZERO)
        }.getOrNull()

    private companion object {
        val logger = KotlinLogging.logger {}
        const val RETRY_AFTER_HEADER = "Retry-After"

        fun withJitter(backoff: Duration): Duration {
            val halfMillis = (backoff.toMillis() / 2).coerceAtLeast(1)
            return Duration.ofMillis(halfMillis + ThreadLocalRandom.current().nextLong(halfMillis + 1))
        }
    }
}
