package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.PostTitleGenerator
import org.every.nook.api.application.post.UpdatePostPlaceBookmarkUseCase
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.UpdatePostPlaceBookmarkPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PostUseCaseConfig {
    @Bean
    fun extractPostContentUseCase(extractors: List<PostContentExtractor>): ExtractPostContentUseCase =
        ExtractPostContentUseCase(extractors)

    @Bean
    fun createPostUseCase(
        extractPostContentUseCase: ExtractPostContentUseCase,
        postTitleGenerator: PostTitleGenerator,
        postMediaStoragePort: PostMediaStoragePort,
        createPostPort: CreatePostPort,
    ): CreatePostUseCase = CreatePostUseCase(
        extractPostContentUseCase = extractPostContentUseCase,
        postTitleGenerator = postTitleGenerator,
        postMediaStoragePort = postMediaStoragePort,
        createPostPort = createPostPort,
    )

    @Bean
    fun findPostPlaceParsingUseCase(findPostPlaceParsingPort: FindPostPlaceParsingPort): FindPostPlaceParsingUseCase =
        FindPostPlaceParsingUseCase(findPostPlaceParsingPort)

    @Bean
    fun updatePostPlaceBookmarkUseCase(
        updatePostPlaceBookmarkPort: UpdatePostPlaceBookmarkPort,
    ): UpdatePostPlaceBookmarkUseCase = UpdatePostPlaceBookmarkUseCase(updatePostPlaceBookmarkPort)
}
