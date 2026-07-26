package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.UnsupportedPostUrlException
import java.net.URI

class InstagramContentUrl private constructor(val canonicalUrl: String, val shortcode: String, val kind: Kind) {
    enum class Kind {
        POST,
        REEL,
    }

    companion object {
        private val allowedHosts = setOf("instagram.com", "www.instagram.com")
        private val shortcodePattern = Regex("[A-Za-z0-9_-]+")

        fun supports(value: String): Boolean = parseOrNull(value) != null

        fun parse(value: String): InstagramContentUrl = parseOrNull(value) ?: throw UnsupportedPostUrlException()

        private fun parseOrNull(value: String): InstagramContentUrl? = runCatching {
            val uri = URI(value.trim())
            require(hasAllowedOrigin(uri))
            val segments = uri.path.split('/').filter(String::isNotBlank)
            require(segments.size == 2)
            val kind = when (segments[0]) {
                "p" -> Kind.POST
                "reel" -> Kind.REEL
                else -> error("Unsupported Instagram content kind")
            }
            val shortcode = segments[1]
            require(shortcodePattern.matches(shortcode))

            InstagramContentUrl(
                canonicalUrl = "https://www.instagram.com/${segments[0]}/$shortcode/",
                shortcode = shortcode,
                kind = kind,
            )
        }.getOrNull()

        private fun hasAllowedOrigin(uri: URI): Boolean {
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return false
            }
            return uri.host?.lowercase() in allowedHosts &&
                uri.userInfo == null &&
                uri.port == -1
        }
    }
}
