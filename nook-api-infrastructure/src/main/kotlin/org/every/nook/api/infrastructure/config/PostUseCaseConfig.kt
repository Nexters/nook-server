package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.GetSavedPostDetailUseCase
import org.every.nook.api.application.post.ListSavedPostsUseCase
import org.every.nook.api.application.post.PostTitleGenerator
import org.every.nook.api.application.post.UpdatePostMemoUseCase
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.every.nook.api.application.post.port.UpdatePostMemoPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PostUseCaseConfig {
    @Bean
    fun extractPostContentUseCase(extractors: List<PostContentExtractor>): ExtractPostContentUseCase =
        ExtractPostContentUseCase(extractors)

    @Bean
    fun createPostUseCase(
        groupOwnershipPort: GroupOwnershipPort,
        postSourceResolver: PostSourceResolver,
        findExistingPostPort: FindExistingPostPort,
        reusePostPort: ReusePostPort,
        extractPostContentUseCase: ExtractPostContentUseCase,
        postTitleGenerator: PostTitleGenerator,
        postMediaStoragePort: PostMediaStoragePort,
        createPostPort: CreatePostPort,
    ): CreatePostUseCase = CreatePostUseCase(
        groupOwnershipPort = groupOwnershipPort,
        postSourceResolver = postSourceResolver,
        findExistingPostPort = findExistingPostPort,
        reusePostPort = reusePostPort,
        extractPostContentUseCase = extractPostContentUseCase,
        postTitleGenerator = postTitleGenerator,
        postMediaStoragePort = postMediaStoragePort,
        createPostPort = createPostPort,
    )

    @Bean
    fun findPostPlaceParsingUseCase(findPostPlaceParsingPort: FindPostPlaceParsingPort): FindPostPlaceParsingUseCase =
        FindPostPlaceParsingUseCase(findPostPlaceParsingPort)

    @Bean
    fun listSavedPostsUseCase(savedPostQueryPort: SavedPostQueryPort): ListSavedPostsUseCase =
        ListSavedPostsUseCase(savedPostQueryPort)

    @Bean
    fun getSavedPostDetailUseCase(savedPostQueryPort: SavedPostQueryPort): GetSavedPostDetailUseCase =
        GetSavedPostDetailUseCase(savedPostQueryPort)

    @Bean
    fun updatePostMemoUseCase(updatePostMemoPort: UpdatePostMemoPort): UpdatePostMemoUseCase =
        UpdatePostMemoUseCase(updatePostMemoPort)
}
