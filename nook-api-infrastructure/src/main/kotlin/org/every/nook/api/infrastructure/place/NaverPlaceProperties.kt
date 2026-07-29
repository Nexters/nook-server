package org.every.nook.api.infrastructure.place

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("external.naver-map")
data class NaverPlaceProperties(
    val baseUrl: String = "https://maps.apigw.ntruss.com",
    val clientId: String = "",
    val clientSecret: String = "",
    val connectTimeout: Duration = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS),
    val readTimeout: Duration = Duration.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS),
) {
    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 3L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 5L
    }
}
