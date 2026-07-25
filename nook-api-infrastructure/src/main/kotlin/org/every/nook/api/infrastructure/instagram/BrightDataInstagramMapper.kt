package org.every.nook.api.infrastructure.instagram

import com.fasterxml.jackson.databind.JsonNode
import org.every.nook.api.application.instagram.ExtractedInstagramContent
import org.every.nook.api.application.instagram.InstagramContentUrl
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Instant

class BrightDataInstagramMapper {
    fun map(url: InstagramContentUrl, record: BrightDataInstagramRecord): ExtractedInstagramContent {
        val media = mapMedia(url, record)
        val locationDetails = record.locationDetails?.let {
            ExtractedInstagramContent.LocationDetails(
                id = it.pk?.asText()?.takeIf(String::isNotBlank),
                name = it.name?.takeIf(String::isNotBlank),
                latitude = it.lat.toNullableDouble(),
                longitude = it.lng.toNullableDouble(),
                imageUrl = it.profilePicUrl?.takeIf(String::isNotBlank),
            )
        }?.takeUnless { details ->
            details.id == null &&
                details.name == null &&
                details.latitude == null &&
                details.longitude == null &&
                details.imageUrl == null
        }

        return ExtractedInstagramContent(
            post = Post(
                source = PostSource(INSTAGRAM_SOURCE, url.shortcode),
                canonicalUrl = url.canonicalUrl,
                authorIdentifier = record.userPosted?.takeIf(String::isNotBlank),
                body = record.description,
                publishedAt = record.datePosted.toInstantOrNull(),
                media = media,
            ),
            contentType = resolveContentType(url, record, media),
            hashtags = record.hashtags.orEmpty().filter(String::isNotBlank),
            thumbnailUrl = record.thumbnail?.takeIf(String::isNotBlank),
            locationNames = record.location.orEmpty().filterNotNull().filter(String::isNotBlank),
            locationDetails = locationDetails,
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

    private fun resolveContentType(
        url: InstagramContentUrl,
        record: BrightDataInstagramRecord,
        media: List<PostMedia>,
    ): ExtractedInstagramContent.ContentType {
        if (url.kind == InstagramContentUrl.Kind.REEL) {
            return ExtractedInstagramContent.ContentType.REEL
        }
        return when (record.contentType?.lowercase()) {
            "carousel" -> ExtractedInstagramContent.ContentType.CAROUSEL

            "video", "reel" -> ExtractedInstagramContent.ContentType.VIDEO

            "image" -> ExtractedInstagramContent.ContentType.IMAGE

            else -> when {
                media.size > 1 -> ExtractedInstagramContent.ContentType.CAROUSEL

                media.singleOrNull()?.type == PostMedia.MediaType.VIDEO ->
                    ExtractedInstagramContent.ContentType.VIDEO

                else -> ExtractedInstagramContent.ContentType.IMAGE
            }
        }
    }

    private fun String?.toMediaType(): PostMedia.MediaType =
        if (equals("Video", ignoreCase = true)) PostMedia.MediaType.VIDEO else PostMedia.MediaType.IMAGE

    private fun JsonNode?.toNullableDouble(): Double? = this?.takeUnless(JsonNode::isNull)?.asText()?.toDoubleOrNull()

    private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private companion object {
        const val INSTAGRAM_SOURCE = "INSTAGRAM"
    }
}
