package org.every.nook.api.application.instagram

import java.net.URI

class InstagramContentUrl private constructor(val canonicalUrl: String, val shortcode: String, val kind: Kind) {
    enum class Kind {
        POST,
        REEL,
    }

    companion object {
        private val allowedHosts = setOf("instagram.com", "www.instagram.com")
        private val shortcodePattern = Regex("[A-Za-z0-9_-]+")

        fun parse(value: String): InstagramContentUrl {
            val uri = runCatching { URI(value.trim()) }
                .getOrElse { invalidUrl() }
            if (!hasAllowedOrigin(uri)) {
                invalidUrl()
            }

            val segments = uri.path.split('/').filter(String::isNotBlank)
            if (segments.size != 2) {
                invalidUrl()
            }
            val kind = when (segments[0]) {
                "p" -> Kind.POST
                "reel" -> Kind.REEL
                else -> invalidUrl()
            }
            val shortcode = segments[1]
            if (!shortcodePattern.matches(shortcode)) {
                invalidUrl()
            }

            return InstagramContentUrl(
                canonicalUrl = "https://www.instagram.com/${segments[0]}/$shortcode/",
                shortcode = shortcode,
                kind = kind,
            )
        }

        private fun hasAllowedOrigin(uri: URI): Boolean {
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return false
            }
            val hasAllowedAuthority = uri.host?.lowercase() in allowedHosts &&
                uri.userInfo == null &&
                uri.port == -1
            return hasAllowedAuthority
        }

        private fun invalidUrl(): Nothing = throw InvalidInstagramUrlException()
    }
}
