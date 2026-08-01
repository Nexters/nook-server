package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.domain.post.PostMedia
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ApifyInstagramMapperTest {
    @Test
    fun `maps Apify post fields`() {
        val result = ApifyInstagramMapper().map(
            InstagramContentUrl.parse("https://www.instagram.com/p/Post123/"),
            ApifyInstagramRecord(
                type = "Image",
                shortCode = "Post123",
                caption = "caption",
                hashtags = listOf("seoul", "food"),
                url = "https://www.instagram.com/p/Post123/",
                displayUrl = "https://cdn.example/image.jpg",
                images = null,
                videoUrl = null,
                timestamp = "2026-08-01T01:02:03.000Z",
                childPosts = null,
                ownerUsername = "owner",
                locationName = "Nook Cafe",
                error = null,
                errorDescription = null,
            ),
        )

        assertEquals("owner", result.post.authorIdentifier)
        assertEquals("caption", result.post.body)
        assertEquals(Instant.parse("2026-08-01T01:02:03Z"), result.post.publishedAt)
        assertEquals(listOf("seoul", "food"), result.hashtags)
        assertEquals(listOf("Nook Cafe"), result.sourceLocationNames)
        assertEquals(PostMedia.MediaType.IMAGE, result.post.media.single().type)
    }

    @Test
    fun `maps carousel children in sequence`() {
        val result = ApifyInstagramMapper().map(
            InstagramContentUrl.parse("https://www.instagram.com/p/Post123/"),
            ApifyInstagramRecord(
                type = "Sidecar",
                shortCode = "Post123",
                caption = null,
                hashtags = null,
                url = null,
                displayUrl = null,
                images = null,
                videoUrl = null,
                timestamp = null,
                childPosts = listOf(
                    ApifyInstagramRecord.ChildPost("Image", "https://cdn.example/1.jpg", null),
                    ApifyInstagramRecord.ChildPost("Video", "https://cdn.example/2.jpg", "https://cdn.example/2.mp4"),
                ),
                ownerUsername = null,
                locationName = null,
                error = null,
                errorDescription = null,
            ),
        )

        assertEquals(listOf(0, 1), result.post.media.map { it.sequence })
        assertEquals(
            listOf(PostMedia.MediaType.IMAGE, PostMedia.MediaType.VIDEO),
            result.post.media.map { it.type },
        )
    }
}
