package org.every.nook.api.infrastructure.openai

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.openai")
data class OpenAiProperties(
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val model: String = "gpt-5-nano",
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
    val maxConcurrentRequests: Int = DEFAULT_MAX_CONCURRENT_REQUESTS,
    val rateLimitRetryBackoffs: List<Duration> = listOf(
        Duration.ofSeconds(FIRST_RATE_LIMIT_RETRY_SECONDS),
        Duration.ofSeconds(SECOND_RATE_LIMIT_RETRY_SECONDS),
        Duration.ofSeconds(THIRD_RATE_LIMIT_RETRY_SECONDS),
    ),
) {
    init {
        require(maxConcurrentRequests > 0) { "OpenAI max concurrent requests must be positive" }
        require(rateLimitRetryBackoffs.all { !it.isNegative && !it.isZero }) {
            "OpenAI rate limit retry backoffs must be positive"
        }
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
        const val DEFAULT_MAX_CONCURRENT_REQUESTS = 4
        const val FIRST_RATE_LIMIT_RETRY_SECONDS = 1L
        const val SECOND_RATE_LIMIT_RETRY_SECONDS = 2L
        const val THIRD_RATE_LIMIT_RETRY_SECONDS = 4L
    }
}
