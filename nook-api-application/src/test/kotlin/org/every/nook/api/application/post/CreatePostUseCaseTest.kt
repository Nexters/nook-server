package org.every.nook.api.application.post

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostContentProviderTimeoutException
import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.CreatedPost
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreatePostUseCaseTest {
    @Test
    fun `stores extracted metadata and media before persisting the post`() {
        val calls = mutableListOf<String>()
        val extractor = object : PostContentExtractor {
            override fun supports(url: String): Boolean = url.startsWith("https://www.instagram.com/")

            override fun extract(url: String): ExtractedPostContent {
                calls += "extractor"
                return ExtractedPostContent(
                    post = Post(
                        source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
                        canonicalUrl = "https://www.instagram.com/p/ABC123/",
                        authorIdentifier = "nook_user",
                        body = "Nook cafe",
                        publishedAt = Instant.parse("2026-07-26T00:00:00Z"),
                        media = listOf(PostMedia(PostMedia.MediaType.IMAGE, "https://source/image.jpg", 0)),
                    ),
                    hashtags = listOf("#cafe", "cafe", "seoul", "x".repeat(Post.MAX_HASHTAG_LENGTH + 1)),
                    sourceLocationNames = listOf("", "Nook Seoul"),
                )
            }
        }
        val mediaStorage = PostMediaStoragePort { media ->
            calls += "media"
            media.copy(url = "https://cdn/image.jpg")
        }
        val titleGenerator = PostTitleGenerator {
            calls += "title"
            "용산 맛집 방문"
        }
        val persistence = CreatePostPort { userId, post, memo, groupIds ->
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
            assertEquals("주말에 방문", memo)
            assertEquals(setOf(1L, 2L), groupIds)
            assertEquals("https://cdn/image.jpg", post.media.single().url)
            assertEquals("용산 맛집 방문", post.title)
            CreatedPost(11, PlaceParsingStatus.PENDING)
        }
        val useCase = CreatePostUseCase(
            GroupOwnershipPort { userId, groupIds ->
                calls += "groups"
                assertEquals(7, userId)
                assertEquals(setOf(1L, 2L), groupIds)
                true
            },
            ExtractPostContentUseCase(listOf(extractor)),
            titleGenerator,
            mediaStorage,
            persistence,
        )

        val result = useCase(
            CreatePostUseCase.Command(
                userId = 7,
                url = "https://www.instagram.com/p/ABC123/?igsh=tracking-value",
                memo = "주말에 방문",
                groupIds = listOf(1, 2, 1),
            ),
        )

        assertEquals(listOf("groups", "extractor", "title", "media", "persistence"), calls)
        assertEquals(11, result.postId)
        assertEquals(PlaceParsingStatusView.PENDING, result.placeParsingStatus)
    }

    @Test
    fun `rejects an unsupported post URL`() {
        val useCase = CreatePostUseCase(
            GroupOwnershipPort { _, _ -> true },
            ExtractPostContentUseCase(emptyList()),
            PostTitleGenerator { error("title generator must not be called") },
            PostMediaStoragePort { it },
            CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<UnsupportedPostUrlException> {
            useCase(CreatePostUseCase.Command(7, "https://example.com/p/ABC123/", groupIds = listOf(1)))
        }
    }

    @Test
    fun `propagates extractor timeout without starting persistence`() {
        val extractor = object : PostContentExtractor {
            override fun supports(url: String): Boolean = true

            override fun extract(url: String): ExtractedPostContent = throw PostContentProviderTimeoutException()
        }
        val useCase = CreatePostUseCase(
            GroupOwnershipPort { _, _ -> true },
            ExtractPostContentUseCase(listOf(extractor)),
            PostTitleGenerator { error("title generator must not be called") },
            PostMediaStoragePort { it },
            CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<PostContentProviderTimeoutException> {
            useCase(
                CreatePostUseCase.Command(
                    userId = 7,
                    url = "https://www.instagram.com/p/ABC123/",
                    groupIds = listOf(1),
                ),
            )
        }
    }

    @Test
    fun `rejects an empty group list before calling external providers`() {
        val useCase = CreatePostUseCase(
            GroupOwnershipPort { _, _ -> error("group ownership must not be queried") },
            ExtractPostContentUseCase(emptyList()),
            PostTitleGenerator { error("title generator must not be called") },
            PostMediaStoragePort { error("media storage must not be called") },
            CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<InvalidGroupException> {
            useCase(
                CreatePostUseCase.Command(
                    userId = 7,
                    url = "https://www.instagram.com/p/ABC123/",
                    groupIds = emptyList(),
                ),
            )
        }
    }

    @Test
    fun `rejects inaccessible groups before calling external providers`() {
        val useCase = CreatePostUseCase(
            GroupOwnershipPort { userId, groupIds ->
                assertEquals(7, userId)
                assertEquals(setOf(1L, 2L), groupIds)
                false
            },
            ExtractPostContentUseCase(emptyList()),
            PostTitleGenerator { error("title generator must not be called") },
            PostMediaStoragePort { error("media storage must not be called") },
            CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<GroupNotFoundException> {
            useCase(
                CreatePostUseCase.Command(
                    userId = 7,
                    url = "https://www.instagram.com/p/ABC123/",
                    groupIds = listOf(1, 2),
                ),
            )
        }
    }
}
