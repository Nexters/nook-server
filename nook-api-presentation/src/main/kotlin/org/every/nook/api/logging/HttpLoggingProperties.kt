package org.every.nook.api.logging

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.logging.http")
data class HttpLoggingProperties(
    val enabled: Boolean = true,
    val requestIdHeader: String = RequestLoggingFields.REQUEST_ID_HEADER,
    val ignoredPathPrefixes: List<String> = listOf(
        "/actuator/health",
        "/actuator/info",
        "/actuator/prometheus",
        "/v3/api-docs",
        "/swagger-ui",
    ),
    val headers: HeaderProperties = HeaderProperties(),
    val body: BodyProperties = BodyProperties(),
) {
    data class HeaderProperties(
        val included: List<String> = listOf(
            "User-Agent",
            "Referer",
            "Origin",
            "X-Forwarded-For",
            "X-Real-IP",
            "X-App-Version",
            "X-Platform",
            "X-Device-Id",
        ),
    )

    data class BodyProperties(
        val requestEnabled: Boolean = false,
        val responseEnabled: Boolean = false,
        val maxBytes: Int = DEFAULT_MAX_BYTES,
        val maxFlattenedFields: Int = DEFAULT_MAX_FLATTENED_FIELDS,
        val maxDepth: Int = DEFAULT_MAX_DEPTH,
        val maxArrayItems: Int = DEFAULT_MAX_ARRAY_ITEMS,
        val includedContentTypes: List<String> = listOf(
            "application/json",
            "application/*+json",
        ),
        val sensitiveFieldKeywords: List<String> = listOf(
            "authorization",
            "cookie",
            "credential",
            "password",
            "secret",
            "token",
            "privatekey",
            "signature",
            "signedurl",
            "presigned",
        ),
    ) {
        companion object {
            const val DEFAULT_MAX_BYTES = 16 * 1024
            const val DEFAULT_MAX_FLATTENED_FIELDS = 80
            const val DEFAULT_MAX_DEPTH = 5
            const val DEFAULT_MAX_ARRAY_ITEMS = 10
        }
    }
}
