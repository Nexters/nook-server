package org.every.nook.api.application.save

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.ExtractedInstagramContent
import org.every.nook.api.application.instagram.InstagramContentProvider
import org.every.nook.api.application.instagram.InstagramProviderTimeoutException
import org.every.nook.api.application.post.PostTitleGenerator
import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.port.PostMediaStoragePort
import org.every.nook.api.application.save.port.SaveInstagramPostPort
import org.every.nook.api.application.save.port.SavedInstagramPost
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaveInstagramPostUseCaseTest {
    @Test
    fun `stores extracted metadata and media before persisting the saved post`() {
        val calls = mutableListOf<String>()
        val provider = InstagramContentProvider {
            calls += "provider"
            extractedContent()
        }
        val mediaStorage = PostMediaStoragePort { media ->
            calls += "media"
            media.copy(url = "https://cdn/image.jpg")
        }
        val titleGenerator = PostTitleGenerator {
            calls += "title"
            "용산 맛집 방문"
        }
        val persistence = SaveInstagramPostPort { userId, post ->
            calls += "persistence"
            assertEquals(7, userId)
            assertEquals("nook_user", post.authorIdentifier)
            assertEquals("Nook cafe", post.body)
            assertEquals(Instant.parse("2026-07-26T00:00:00Z"), post.publishedAt)
            assertEquals(listOf("cafe", "seoul"), post.hashtags)
            assertEquals("Nook Seoul", post.sourceLocationTag)
            assertEquals(
                "https://www.instagram.com/p/ABC123/?igsh=tracking-value",
                post.canonicalUrl,
            )
            assertEquals("주말에 방문", post.memo)
            assertEquals("https://cdn/image.jpg", post.media.single().url)
            assertEquals("용산 맛집 방문", post.title)
            SavedInstagramPost(11, 13, PlaceParsingStatus.PENDING)
        }
        val useCase = SaveInstagramPostUseCase(
            ExtractInstagramContentUseCase(provider),
            titleGenerator,
            mediaStorage,
            persistence,
        )

        val result = useCase(
            SaveInstagramPostUseCase.Command(
                7,
                "https://www.instagram.com/p/ABC123/?igsh=tracking-value",
                "주말에 방문",
            ),
        )

        assertEquals(listOf("provider", "title", "media", "persistence"), calls)
        assertEquals(11, result.savedPostId)
        assertEquals(13, result.postId)
        assertEquals(PlaceParsingStatusView.PENDING, result.placeParsingStatus)
    }

    @Test
    fun `keeps the saved post invalid URL error contract`() {
        val useCase = SaveInstagramPostUseCase(
            ExtractInstagramContentUseCase { error("provider must not be called") },
            PostTitleGenerator { error("title generator must not be called") },
            PostMediaStoragePort { it },
            SaveInstagramPostPort { _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<InvalidInstagramPostUrlException> {
            useCase(SaveInstagramPostUseCase.Command(7, "https://example.com/p/ABC123/"))
        }
    }

    @Test
    fun `propagates provider timeout without starting persistence`() {
        val useCase = SaveInstagramPostUseCase(
            ExtractInstagramContentUseCase { throw InstagramProviderTimeoutException() },
            PostTitleGenerator { error("title generator must not be called") },
            PostMediaStoragePort { it },
            SaveInstagramPostPort { _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<InstagramProviderTimeoutException> {
            useCase(SaveInstagramPostUseCase.Command(7, "https://www.instagram.com/p/ABC123/"))
        }
    }

    private fun extractedContent(): ExtractedInstagramContent = ExtractedInstagramContent(
        post = Post(
            source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
            canonicalUrl = "https://www.instagram.com/p/ABC123/",
            authorIdentifier = "nook_user",
            body = "Nook cafe",
            publishedAt = Instant.parse("2026-07-26T00:00:00Z"),
            media = listOf(PostMedia(PostMedia.MediaType.IMAGE, "https://source/image.jpg", 0)),
        ),
        contentType = ExtractedInstagramContent.ContentType.IMAGE,
        hashtags = listOf("#cafe", "cafe", "seoul", "x".repeat(Post.MAX_HASHTAG_LENGTH + 1)),
        thumbnailUrl = null,
        locationNames = listOf("", "Nook Seoul"),
        locationDetails = null,
    )
}
