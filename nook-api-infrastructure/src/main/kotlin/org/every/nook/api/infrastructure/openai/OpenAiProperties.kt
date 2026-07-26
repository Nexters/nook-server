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
) {
    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
    }
}
