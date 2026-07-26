package org.every.nook.api.application.post

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.Post

class CreatePostUseCase(
    private val extractPostContentUseCase: ExtractPostContentUseCase,
    private val postTitleGenerator: PostTitleGenerator,
    private val postMediaStoragePort: PostMediaStoragePort,
    private val createPostPort: CreatePostPort,
) {
    operator fun invoke(command: Command): Result {
        val extractedContent = extractPostContentUseCase(command.url)
        val providedPost = extractedContent.post.copy(
            sourceLocationTag = extractedContent.toSourceLocationTag(),
            hashtags = extractedContent.hashtags.toPersistentHashtags(),
        )
        val storedPost = providedPost.copy(
            canonicalUrl = command.url,
            memo = command.memo,
            title = postTitleGenerator.generate(
                PostTitleGenerator.Request(
                    body = providedPost.body,
                    hashtags = providedPost.hashtags,
                    sourceLocationTag = providedPost.sourceLocationTag,
                ),
            ),
            media = providedPost.media.map(postMediaStoragePort::store),
        )
        val created = createPostPort.create(command.userId, storedPost)

        return Result(
            postId = created.postId,
            placeParsingStatus = PlaceParsingStatusView.from(created.placeParsingStatus),
        )
    }

    private fun ExtractedPostContent.toSourceLocationTag(): String? = sourceLocationNames
        .asSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() && it.length <= Post.MAX_SOURCE_LOCATION_TAG_LENGTH }

    private fun List<String>.toPersistentHashtags(): List<String> = asSequence()
        .map(String::trim)
        .map { it.removePrefix("#").trim() }
        .filter { it.isNotEmpty() && it.length <= Post.MAX_HASHTAG_LENGTH }
        .distinct()
        .toList()

    data class Command(val userId: Long, val url: String, val memo: String? = null)

    data class Result(val postId: Long, val placeParsingStatus: PlaceParsingStatusView)
}
