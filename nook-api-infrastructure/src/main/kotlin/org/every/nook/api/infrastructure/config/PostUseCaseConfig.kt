package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.PostContentExtractor
import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.post.CreatePostUseCase
import org.every.nook.api.application.post.DeleteSavedPostUseCase
import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.GetSavedPostDetailUseCase
import org.every.nook.api.application.post.ListSavedPostsUseCase
import org.every.nook.api.application.post.PostContentInference
import org.every.nook.api.application.post.PostContentParsingJobPort
import org.every.nook.api.application.post.ProcessPostContentParsingJobUseCase
import org.every.nook.api.application.post.StorePostMediaUseCase
import org.every.nook.api.application.post.UpdatePostMemoUseCase
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.DeleteSavedPostPort
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.every.nook.api.application.post.port.UpdatePostMediaUrlPort
import org.every.nook.api.application.post.port.UpdatePostMemoPort
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.ProcessingTracePort
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PostContentParsingProperties::class)
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
        createPostPort: CreatePostPort,
    ): CreatePostUseCase = CreatePostUseCase(
        groupOwnershipPort = groupOwnershipPort,
        postSourceResolver = postSourceResolver,
        findExistingPostPort = findExistingPostPort,
        reusePostPort = reusePostPort,
        createPostPort = createPostPort,
    )

    @Bean
    fun processPostContentParsingJobUseCase(
        jobPort: PostContentParsingJobPort,
        extractPostContentUseCase: ExtractPostContentUseCase,
        postContentInference: PostContentInference,
        imageTextExtractor: ImageTextExtractor,
        processingMetrics: ObjectProvider<ProcessingMetrics>,
        processingTracePort: ProcessingTracePort,
        properties: PostContentParsingProperties,
    ): ProcessPostContentParsingJobUseCase = ProcessPostContentParsingJobUseCase(
        jobPort = jobPort,
        extractPostContent = extractPostContentUseCase,
        contentInference = postContentInference,
        imageTextExtractor = imageTextExtractor,
        retryBackoffs = properties.retryBackoffs,
        processingTimeout = properties.processingTimeout,
        metrics = processingMetrics.ifAvailable ?: NoOpProcessingMetrics,
        tracePort = processingTracePort,
    )

    @Bean
    fun storePostMediaUseCase(
        postMediaStoragePort: PostMediaStoragePort,
        updatePostMediaUrlPort: UpdatePostMediaUrlPort,
        processingMetrics: ObjectProvider<ProcessingMetrics>,
    ): StorePostMediaUseCase = StorePostMediaUseCase(
        mediaStorage = postMediaStoragePort,
        updateMediaUrl = updatePostMediaUrlPort,
        metrics = processingMetrics.ifAvailable ?: NoOpProcessingMetrics,
    )

    @Bean
    fun findOutstandingPostContentParsingJobsUseCase(
        jobPort: PostContentParsingJobPort,
        properties: PostContentParsingProperties,
        @Value("\${parsing.dispatcher-batch-size:20}") batchSize: Int,
    ): FindOutstandingPostContentParsingJobsUseCase = FindOutstandingPostContentParsingJobsUseCase(
        jobPort = jobPort,
        processingTimeout = properties.processingTimeout,
        batchSize = batchSize,
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

    @Bean
    fun deleteSavedPostUseCase(deleteSavedPostPort: DeleteSavedPostPort): DeleteSavedPostUseCase =
        DeleteSavedPostUseCase(deleteSavedPostPort)
}
