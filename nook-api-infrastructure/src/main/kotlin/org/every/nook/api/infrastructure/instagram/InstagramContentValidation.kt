package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentProviderException
import org.every.nook.api.application.error.NookException
import org.every.nook.api.infrastructure.persistence.cache.ScrapingProviderResponseCache

internal fun ExtractedPostContent.requireUsableContent(): ExtractedPostContent = apply {
    if (post.body.isNullOrBlank() && post.media.isEmpty()) {
        throw PostContentProviderException()
    }
}

internal fun ScrapingProviderResponseCache.findUsableInstagramContent(
    provider: String,
    shortcode: String,
    mapResponse: (String) -> ExtractedPostContent,
): ExtractedPostContent? {
    val cached = find(provider, "INSTAGRAM", shortcode) ?: return null
    return try {
        mapResponse(cached)
    } catch (_: NookException) {
        evict(provider, "INSTAGRAM", shortcode, cached)
        null
    }
}
