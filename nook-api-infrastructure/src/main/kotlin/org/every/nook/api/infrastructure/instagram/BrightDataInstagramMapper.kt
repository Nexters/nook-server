package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Instant

class BrightDataInstagramMapper {
    fun map(url: InstagramContentUrl, record: BrightDataInstagramRecord): ExtractedPostContent {
        val media = mapMedia(url, record)

        return ExtractedPostContent(
            post = Post(
                source = PostSource(INSTAGRAM_SOURCE, url.shortcode),
                canonicalUrl = url.canonicalUrl,
                authorIdentifier = record.userPosted?.takeIf(String::isNotBlank),
                body = record.description,
                publishedAt = record.datePosted.toInstantOrNull(),
                media = media,
            ),
            hashtags = record.hashtags.orEmpty().filter(String::isNotBlank),
            sourceLocationNames = buildList {
                addAll(record.location.orEmpty().filterNotNull().filter(String::isNotBlank))
                record.locationDetails?.name?.takeIf(String::isNotBlank)?.let(::add)
            }.distinct(),
        )
    }

    private fun mapMedia(url: InstagramContentUrl, record: BrightDataInstagramRecord): List<PostMedia> {
        val postContent = record.postContent.orEmpty()
            .filter { !it.url.isNullOrBlank() }
            .sortedBy { it.index ?: Int.MAX_VALUE }
            .mapIndexed { sequence, item ->
                PostMedia(
                    type = item.type.toMediaType(),
                    url = requireNotNull(item.url),
                    sequence = sequence,
                )
            }
        if (postContent.isNotEmpty()) {
            return postContent
        }

        val photos = record.photos.orEmpty().filter(String::isNotBlank)
        val videos = buildList {
            addAll(record.videos.orEmpty().filter(String::isNotBlank))
            if (url.kind == InstagramContentUrl.Kind.REEL && !record.videoUrl.isNullOrBlank()) {
                add(record.videoUrl)
            }
        }

        return (
            photos.map { PostMedia.MediaType.IMAGE to it } +
                videos.distinct().map { PostMedia.MediaType.VIDEO to it }
            )
            .mapIndexed { sequence, (type, mediaUrl) -> PostMedia(type, mediaUrl, sequence) }
    }

    private fun String?.toMediaType(): PostMedia.MediaType =
        if (equals("Video", ignoreCase = true)) PostMedia.MediaType.VIDEO else PostMedia.MediaType.IMAGE

    private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private companion object {
        const val INSTAGRAM_SOURCE = "INSTAGRAM"
    }
}
