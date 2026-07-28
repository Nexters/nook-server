package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.domain.post.PostSource

class InstagramPostSourceResolver : PostSourceResolver {
    override fun resolve(url: String): PostSource? {
        if (!InstagramContentUrl.supports(url)) {
            return null
        }
        val instagramUrl = InstagramContentUrl.parse(url)
        return PostSource(type = INSTAGRAM_SOURCE, externalPostId = instagramUrl.shortcode)
    }

    private companion object {
        const val INSTAGRAM_SOURCE = "INSTAGRAM"
    }
}
