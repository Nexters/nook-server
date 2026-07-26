package org.every.nook.api.infrastructure.provider

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.ExtractedInstagramContent
import org.every.nook.api.application.instagram.InstagramContentProvider
import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BrightDataInstagramPostProviderAdapterTest {
    @Test
    fun `maps extracted Instagram content to a persistable post`() {
        val extracted = extractedContent(
            hashtags = listOf("#서울", "맛집", "#서울"),
            locationNames = listOf("용산"),
            locationDetails = ExtractedInstagramContent.LocationDetails(
                id = "place-1",
                name = "원동미나리삼겹살",
                latitude = 37.5422437,
                longitude = 126.9722887,
                imageUrl = null,
            ),
        )
        val adapter = adapterReturning(extracted)

        val post = adapter.fetch(extracted.canonicalUrl)

        assertEquals(extracted.post.source, post.source)
        assertEquals("nook", post.authorIdentifier)
        assertEquals("설명", post.body)
        assertEquals(Instant.parse("2026-07-26T00:00:00Z"), post.publishedAt)
        assertEquals(extracted.post.media, post.media)
        assertEquals(listOf("서울", "맛집"), post.hashtags)
        assertEquals("원동미나리삼겹살", post.sourceLocationTag)
    }

    @Test
    fun `uses the first location name when details are absent`() {
        val adapter = adapterReturning(
            extractedContent(
                hashtags = emptyList(),
                locationNames = listOf("성수동", "서울"),
                locationDetails = null,
            ),
        )

        val post = adapter.fetch("https://www.instagram.com/p/ABC123/")

        assertEquals("성수동", post.sourceLocationTag)
    }

    @Test
    fun `allows content without location information`() {
        val adapter = adapterReturning(
            extractedContent(
                hashtags = emptyList(),
                locationNames = emptyList(),
                locationDetails = null,
            ),
        )

        val post = adapter.fetch("https://www.instagram.com/reel/ABC123/")

        assertNull(post.sourceLocationTag)
    }

    @Test
    fun `preserves the saved post invalid URL error contract`() {
        val adapter = adapterReturning(
            extractedContent(
                hashtags = emptyList(),
                locationNames = emptyList(),
                locationDetails = null,
            ),
        )

        assertFailsWith<InvalidInstagramPostUrlException> {
            adapter.fetch("https://www.instagram.com/stories/nook/123/")
        }
    }

    private fun adapterReturning(content: ExtractedInstagramContent): BrightDataInstagramPostProviderAdapter =
        BrightDataInstagramPostProviderAdapter(
            ExtractInstagramContentUseCase(
                InstagramContentProvider { content },
            ),
        )

    private fun extractedContent(
        hashtags: List<String>,
        locationNames: List<String>,
        locationDetails: ExtractedInstagramContent.LocationDetails?,
    ): ExtractedInstagramContent = ExtractedInstagramContent(
        post = Post(
            source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
            canonicalUrl = "https://www.instagram.com/p/ABC123/",
            authorIdentifier = "nook",
            body = "설명",
            publishedAt = Instant.parse("2026-07-26T00:00:00Z"),
            media = listOf(
                PostMedia(
                    type = PostMedia.MediaType.IMAGE,
                    url = "https://cdn.example/image.jpg",
                    sequence = 0,
                ),
            ),
        ),
        contentType = ExtractedInstagramContent.ContentType.IMAGE,
        hashtags = hashtags,
        thumbnailUrl = "https://cdn.example/thumbnail.jpg",
        locationNames = locationNames,
        locationDetails = locationDetails,
    )
}
