package org.every.nook.api.infrastructure.provider

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.InvalidInstagramUrlException
import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import org.every.nook.api.application.save.port.InstagramPostProviderPort
import org.every.nook.api.domain.post.Post
import org.springframework.stereotype.Component

@Component
class BrightDataInstagramPostProviderAdapter(
    private val extractInstagramContentUseCase: ExtractInstagramContentUseCase,
) : InstagramPostProviderPort {
    override fun fetch(instagramUrl: String): Post {
        val content = try {
            extractInstagramContentUseCase(instagramUrl)
        } catch (_: InvalidInstagramUrlException) {
            throw InvalidInstagramPostUrlException()
        }
        return content.post.copy(
            sourceLocationTag = content.locationDetails?.name
                ?: content.locationNames.firstOrNull(),
            hashtags = content.hashtags
                .map { hashtag -> hashtag.normalizeHashtag() }
                .filter(String::isNotBlank)
                .distinct(),
        )
    }

    private fun String.normalizeHashtag(): String = trim().removePrefix(HASHTAG_PREFIX)

    private companion object {
        const val HASHTAG_PREFIX = "#"
    }
}
