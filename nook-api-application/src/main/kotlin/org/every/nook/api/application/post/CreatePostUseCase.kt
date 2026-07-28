package org.every.nook.api.application.post

import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostSourceResolver
import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.CreatedPost
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.domain.post.Post

class CreatePostUseCase(
    private val groupOwnershipPort: GroupOwnershipPort,
    private val postSourceResolver: PostSourceResolver,
    private val findExistingPostPort: FindExistingPostPort,
    private val reusePostPort: ReusePostPort,
    private val extractPostContentUseCase: ExtractPostContentUseCase,
    private val postTitleGenerator: PostTitleGenerator,
    private val postMediaStoragePort: PostMediaStoragePort,
    private val createPostPort: CreatePostPort,
) {
    operator fun invoke(command: Command): Result {
        val groupIds = command.groupIds.toSet()
        validateGroups(command.userId, groupIds)
        val source = postSourceResolver.resolve(command.url) ?: throw UnsupportedPostUrlException()
        val existingPost = findExistingPostPort.find(source)
        if (existingPost != null) {
            return reusePostPort.reuse(
                userId = command.userId,
                source = source,
                memo = command.memo,
                groupIds = groupIds,
            ).toResult()
        }
        val extractedContent = extractPostContentUseCase(command.url)
        val providedPost = extractedContent.post.copy(
            sourceLocationTag = extractedContent.toSourceLocationTag(),
            hashtags = extractedContent.hashtags.toPersistentHashtags(),
        )
        val storedPost = providedPost.copy(
            title = postTitleGenerator.generate(
                PostTitleGenerator.Request(
                    body = providedPost.body,
                    hashtags = providedPost.hashtags,
                    sourceLocationTag = providedPost.sourceLocationTag,
                ),
            ),
            media = providedPost.media.map(postMediaStoragePort::store),
        )
        val created = createPostPort.create(
            userId = command.userId,
            post = storedPost,
            memo = command.memo,
            groupIds = groupIds,
        )

        return created.toResult()
    }

    private fun CreatedPost.toResult(): Result = Result(
        postId = postId,
        placeParsingStatus = PlaceParsingStatusView.from(placeParsingStatus),
    )

    private fun validateGroups(userId: Long, groupIds: Set<Long>) {
        if (groupIds.isEmpty()) {
            throw InvalidGroupException(IllegalArgumentException("At least one group is required"))
        }
        if (!groupOwnershipPort.ownsAll(userId, groupIds)) {
            throw GroupNotFoundException()
        }
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

    data class Command(val userId: Long, val url: String, val memo: String? = null, val groupIds: List<Long>)

    data class Result(val postId: Long, val placeParsingStatus: PlaceParsingStatusView)
}
