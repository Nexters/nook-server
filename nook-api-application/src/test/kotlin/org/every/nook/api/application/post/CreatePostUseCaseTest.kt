package org.every.nook.api.application.post

import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PostProcessingStageView
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.CreatedPost
import org.every.nook.api.application.post.port.ExistingPost
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.domain.post.PostSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreatePostUseCaseTest {
    @Test
    fun `persists a placeholder without calling content providers`() {
        val calls = mutableListOf<String>()
        val useCase = CreatePostUseCase(
            groupOwnershipPort = GroupOwnershipPort { userId, groupIds ->
                calls += "groups"
                assertEquals(7, userId)
                assertEquals(setOf(1L, 2L), groupIds)
                true
            },
            postSourceResolver = PostSourceResolver {
                calls += "source"
                SOURCE
            },
            findExistingPostPort = FindExistingPostPort {
                calls += "find"
                null
            },
            reusePostPort = ReusePostPort { _, _, _, _ ->
                error("existing post must not be reused")
            },
            createPostPort = CreatePostPort { userId, post, memo, groupIds ->
                calls += "create"
                assertEquals(7, userId)
                assertEquals(SOURCE, post.source)
                assertEquals("https://www.instagram.com/p/ABC123/", post.canonicalUrl)
                assertEquals(null, post.body)
                assertEquals(emptyList(), post.media)
                assertEquals("주말에 방문", memo)
                assertEquals(setOf(1L, 2L), groupIds)
                CreatedPost(
                    postId = 11,
                    contentParsingStatus = PostContentParsingStatus.PENDING,
                    placeParsingStatus = null,
                )
            },
        )

        val result = useCase(
            CreatePostUseCase.Command(
                userId = 7,
                url = "https://www.instagram.com/p/ABC123/?igsh=tracking-value",
                memo = "주말에 방문",
                groupIds = listOf(1, 2, 1),
            ),
        )

        assertEquals(listOf("groups", "source", "find", "create"), calls)
        assertEquals(11, result.postId)
        assertEquals(PlaceParsingStatusView.PENDING, result.placeParsingStatus)
        assertEquals(PostProcessingStatusView.PENDING, result.processingStatus)
        assertEquals(PostProcessingStageView.CONTENT, result.processingStage)
    }

    @Test
    fun `rejects an unsupported post URL`() {
        val useCase = useCase(
            sourceResolver = PostSourceResolver { null },
            findExisting = FindExistingPostPort { error("existing post must not be queried") },
            create = CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
        )

        assertFailsWith<UnsupportedPostUrlException> {
            useCase(CreatePostUseCase.Command(7, "https://example.com/p/ABC123/", groupIds = listOf(1)))
        }
    }

    @Test
    fun `rejects an empty group list before resolving the source`() {
        val useCase = CreatePostUseCase(
            groupOwnershipPort = GroupOwnershipPort { _, _ -> error("group ownership must not be queried") },
            postSourceResolver = PostSourceResolver { error("source must not be resolved") },
            findExistingPostPort = FindExistingPostPort { error("existing post must not be queried") },
            reusePostPort = ReusePostPort { _, _, _, _ -> error("existing post must not be reused") },
            createPostPort = CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
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
    fun `rejects inaccessible groups before resolving the source`() {
        val useCase = CreatePostUseCase(
            groupOwnershipPort = GroupOwnershipPort { userId, groupIds ->
                assertEquals(7, userId)
                assertEquals(setOf(1L, 2L), groupIds)
                false
            },
            postSourceResolver = PostSourceResolver { error("source must not be resolved") },
            findExistingPostPort = FindExistingPostPort { error("existing post must not be queried") },
            reusePostPort = ReusePostPort { _, _, _, _ -> error("existing post must not be reused") },
            createPostPort = CreatePostPort { _, _, _, _ -> error("persistence must not be called") },
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

    @Test
    fun `reuses an existing post without starting a new content job`() {
        val calls = mutableListOf<String>()
        val useCase = CreatePostUseCase(
            groupOwnershipPort = GroupOwnershipPort { _, _ ->
                calls += "groups"
                true
            },
            postSourceResolver = PostSourceResolver {
                calls += "source"
                SOURCE
            },
            findExistingPostPort = FindExistingPostPort {
                calls += "find"
                ExistingPost(
                    contentParsingStatus = PostContentParsingStatus.COMPLETED,
                    placeParsingStatus = PlaceParsingStatus.COMPLETED,
                )
            },
            reusePostPort = ReusePostPort { userId, source, memo, groupIds ->
                calls += "reuse"
                assertEquals(7, userId)
                assertEquals(SOURCE, source)
                assertEquals("새 메모", memo)
                assertEquals(setOf(1L, 2L), groupIds)
                CreatedPost(
                    postId = 11,
                    contentParsingStatus = PostContentParsingStatus.COMPLETED,
                    placeParsingStatus = PlaceParsingStatus.COMPLETED,
                )
            },
            createPostPort = CreatePostPort { _, _, _, _ -> error("new post must not be persisted") },
        )

        val result = useCase(
            CreatePostUseCase.Command(
                userId = 7,
                url = "https://www.instagram.com/p/ABC123/?img_index=14",
                memo = "새 메모",
                groupIds = listOf(1, 2),
            ),
        )

        assertEquals(listOf("groups", "source", "find", "reuse"), calls)
        assertEquals(11, result.postId)
        assertEquals(PostProcessingStatusView.COMPLETED, result.processingStatus)
        assertEquals(null, result.processingStage)
    }

    private fun useCase(
        sourceResolver: PostSourceResolver = PostSourceResolver { SOURCE },
        findExisting: FindExistingPostPort = FindExistingPostPort { null },
        create: CreatePostPort = CreatePostPort { _, _, _, _ ->
            CreatedPost(11, PostContentParsingStatus.PENDING, null)
        },
    ): CreatePostUseCase = CreatePostUseCase(
        groupOwnershipPort = GroupOwnershipPort { _, _ -> true },
        postSourceResolver = sourceResolver,
        findExistingPostPort = findExisting,
        reusePostPort = ReusePostPort { _, _, _, _ -> error("existing post must not be reused") },
        createPostPort = create,
    )

    private companion object {
        val SOURCE = PostSource(type = "INSTAGRAM", externalPostId = "ABC123")
    }
}
