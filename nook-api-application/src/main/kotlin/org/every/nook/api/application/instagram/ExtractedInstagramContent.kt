package org.every.nook.api.application.instagram

import org.every.nook.api.domain.post.Post

data class ExtractedInstagramContent(
    val post: Post,
    val contentType: ContentType,
    val hashtags: List<String>,
    val thumbnailUrl: String?,
    val locationNames: List<String>,
    val locationDetails: LocationDetails?,
) {
    val canonicalUrl: String
        get() = post.canonicalUrl

    val shortcode: String
        get() = post.source.externalPostId

    val description: String?
        get() = post.body

    val media: List<Media>
        get() = post.media.map {
            Media(
                type = when (it.type.name) {
                    "VIDEO" -> MediaType.VIDEO
                    else -> MediaType.IMAGE
                },
                url = it.url,
                sequence = it.sequence,
            )
        }

    enum class ContentType {
        IMAGE,
        CAROUSEL,
        VIDEO,
        REEL,
    }

    enum class MediaType {
        IMAGE,
        VIDEO,
    }

    data class Media(val type: MediaType, val url: String, val sequence: Int)

    data class LocationDetails(
        val id: String?,
        val name: String?,
        val latitude: Double?,
        val longitude: Double?,
        val imageUrl: String?,
    )
}
