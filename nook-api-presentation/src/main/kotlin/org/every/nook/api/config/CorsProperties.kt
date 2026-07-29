package org.every.nook.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOriginPatterns: List<String> = listOf(
        "http://localhost:*",
        "http://127.0.0.1:*",
        "http://everynook.co.kr",
        "https://everynook.co.kr",
    ),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("*"),
    val exposedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = true,
    val maxAgeSeconds: Long = 3_600,
)
