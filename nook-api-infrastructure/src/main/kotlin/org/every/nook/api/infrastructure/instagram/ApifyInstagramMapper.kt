package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Instant

class ApifyInstagramMapper {
    fun map(url: InstagramContentUrl, record: ApifyInstagramRecord): ExtractedPostContent = ExtractedPostContent(
        post = Post(
            source = PostSource(INSTAGRAM_SOURCE, url.shortcode),
            canonicalUrl = url.canonicalUrl,
            authorIdentifier = record.ownerUsername?.takeIf(String::isNotBlank),
            body = record.caption,
            publishedAt = record.timestamp.toInstantOrNull(),
            media = mapMedia(record),
        ),
        hashtags = record.hashtags.orEmpty().filter(String::isNotBlank),
        sourceLocationNames = listOfNotNull(record.locationName?.takeIf(String::isNotBlank)),
    )

    private fun mapMedia(record: ApifyInstagramRecord): List<PostMedia> {
        val childMedia = record.childPosts.orEmpty().mapNotNull { child ->
            when {
                child.type.isVideo() && !child.videoUrl.isNullOrBlank() ->
                    PostMedia.MediaType.VIDEO to child.videoUrl

                !child.displayUrl.isNullOrBlank() -> PostMedia.MediaType.IMAGE to child.displayUrl

                !child.videoUrl.isNullOrBlank() -> PostMedia.MediaType.VIDEO to child.videoUrl

                else -> null
            }
        }
        val images = record.images.orEmpty().filter(String::isNotBlank)
        return when {
            childMedia.isNotEmpty() -> childMedia.toPostMedia()

            !record.videoUrl.isNullOrBlank() ->
                listOf(PostMedia(PostMedia.MediaType.VIDEO, record.videoUrl, 0))

            images.isNotEmpty() -> images.map { PostMedia.MediaType.IMAGE to it }.toPostMedia()

            else -> listOfNotNull(
                record.displayUrl?.takeIf(String::isNotBlank)
                    ?.let { PostMedia(PostMedia.MediaType.IMAGE, it, 0) },
            )
        }
    }

    private fun List<Pair<PostMedia.MediaType, String>>.toPostMedia(): List<PostMedia> =
        mapIndexed { sequence, (type, mediaUrl) -> PostMedia(type, mediaUrl, sequence) }

    private fun String?.isVideo(): Boolean = equals("Video", ignoreCase = true)

    private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private companion object {
        const val INSTAGRAM_SOURCE = "INSTAGRAM"
    }
}
