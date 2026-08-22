package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.SourceProfileHint
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
        sourceProfileHints = record.taggedUsers.orEmpty().mapNotNull { taggedUser ->
            val displayName = taggedUser.fullName?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val username = taggedUser.username?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            SourceProfileHint(displayName = displayName, username = username)
        }.distinctBy(SourceProfileHint::username),
    )

    private fun mapMedia(record: ApifyInstagramRecord): List<PostMedia> {
        val childMedia = record.childPosts.orEmpty().mapNotNull { child ->
            when {
                child.type.isVideo() && !child.videoUrl.isNullOrBlank() ->
                    MappedMedia(PostMedia.MediaType.VIDEO, child.videoUrl, child.displayUrl.notBlankOrNull())

                !child.displayUrl.isNullOrBlank() -> MappedMedia(PostMedia.MediaType.IMAGE, child.displayUrl)

                !child.videoUrl.isNullOrBlank() ->
                    MappedMedia(PostMedia.MediaType.VIDEO, child.videoUrl, child.displayUrl.notBlankOrNull())

                else -> null
            }
        }
        val images = record.images.orEmpty().filter(String::isNotBlank)
        return when {
            childMedia.isNotEmpty() -> childMedia.toPostMedia()

            !record.videoUrl.isNullOrBlank() ->
                listOf(
                    PostMedia(
                        PostMedia.MediaType.VIDEO,
                        record.videoUrl,
                        0,
                        record.displayUrl.notBlankOrNull() ?: images.firstOrNull(),
                    ),
                )

            images.isNotEmpty() -> images.map { MappedMedia(PostMedia.MediaType.IMAGE, it) }.toPostMedia()

            else -> listOfNotNull(
                record.displayUrl?.takeIf(String::isNotBlank)
                    ?.let { PostMedia(PostMedia.MediaType.IMAGE, it, 0) },
            )
        }
    }

    private fun List<MappedMedia>.toPostMedia(): List<PostMedia> =
        mapIndexed { sequence, media -> PostMedia(media.type, media.url, sequence, media.thumbnailUrl) }

    private fun String?.notBlankOrNull(): String? = this?.takeIf(String::isNotBlank)

    private fun String?.isVideo(): Boolean = equals("Video", ignoreCase = true)

    private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private companion object {
        const val INSTAGRAM_SOURCE = "INSTAGRAM"
    }

    private data class MappedMedia(val type: PostMedia.MediaType, val url: String, val thumbnailUrl: String? = null)
}
