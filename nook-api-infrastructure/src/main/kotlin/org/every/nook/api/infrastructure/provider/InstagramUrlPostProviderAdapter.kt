package org.every.nook.api.infrastructure.provider

import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import org.every.nook.api.application.save.port.InstagramPostProviderPort
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import org.springframework.stereotype.Component
import java.net.URI

@Component
class InstagramUrlPostProviderAdapter : InstagramPostProviderPort {
    override fun fetch(instagramUrl: String): Post {
        val uri = parseUri(instagramUrl)
        val segments = uri.path.split('/').filter(String::isNotBlank)

        if (!isSupportedPostUri(uri, segments)) {
            throw InvalidInstagramPostUrlException()
        }

        val externalPostId = segments.last()
        if (!SHORTCODE_PATTERN.matches(externalPostId)) {
            throw InvalidInstagramPostUrlException()
        }

        val contentType = segments.first()
        return Post(
            source = PostSource(type = INSTAGRAM_SOURCE_TYPE, externalPostId = externalPostId),
            canonicalUrl = "https://www.instagram.com/$contentType/$externalPostId/",
        )
    }

    private fun parseUri(value: String): URI = try {
        URI(value.trim())
    } catch (_: IllegalArgumentException) {
        throw InvalidInstagramPostUrlException()
    }

    private fun isSupportedPostUri(uri: URI, segments: List<String>): Boolean {
        val supportedOrigin = uri.scheme == HTTPS_SCHEME && uri.host?.lowercase() in SUPPORTED_HOSTS
        val supportedPath = segments.size == EXPECTED_PATH_SEGMENT_COUNT &&
            segments.first() in SUPPORTED_CONTENT_TYPES
        return supportedOrigin && supportedPath
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val INSTAGRAM_SOURCE_TYPE = "INSTAGRAM"
        const val EXPECTED_PATH_SEGMENT_COUNT = 2
        val SUPPORTED_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
        val SUPPORTED_CONTENT_TYPES = setOf("p", "reel")
        val SHORTCODE_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
