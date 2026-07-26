package org.every.nook.api.application.save

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.ExtractedInstagramContent
import org.every.nook.api.application.instagram.InvalidInstagramUrlException
import org.every.nook.api.application.post.PostTitleGenerator
import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.port.PostMediaStoragePort
import org.every.nook.api.application.save.port.SaveInstagramPostPort
import org.every.nook.api.domain.post.Post

class SaveInstagramPostUseCase(
    private val extractInstagramContentUseCase: ExtractInstagramContentUseCase,
    private val postTitleGenerator: PostTitleGenerator,
    private val postMediaStoragePort: PostMediaStoragePort,
    private val saveInstagramPostPort: SaveInstagramPostPort,
) {
    operator fun invoke(command: Command): Result {
        if (command.userId <= 0 || command.instagramUrl.isBlank()) {
            throw InvalidInstagramPostUrlException()
        }

        val extractedContent = extractContent(command.instagramUrl)
        val providedPost = extractedContent.post.copy(
            sourceLocationTag = extractedContent.toSourceLocationTag(),
            hashtags = extractedContent.hashtags.toPersistentHashtags(),
        )
        val storedPost = providedPost.copy(
            canonicalUrl = command.instagramUrl,
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
        val saved = saveInstagramPostPort.save(command.userId, storedPost)

        return Result(
            savedPostId = saved.savedPostId,
            postId = saved.postId,
            placeParsingStatus = PlaceParsingStatusView.from(saved.placeParsingStatus),
        )
    }

    private fun extractContent(instagramUrl: String): ExtractedInstagramContent = try {
        extractInstagramContentUseCase(instagramUrl)
    } catch (_: InvalidInstagramUrlException) {
        throw InvalidInstagramPostUrlException()
    }

    private fun ExtractedInstagramContent.toSourceLocationTag(): String? = sequence {
        yieldAll(locationNames)
        locationDetails?.name?.let { yield(it) }
    }
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() && it.length <= Post.MAX_SOURCE_LOCATION_TAG_LENGTH }

    private fun List<String>.toPersistentHashtags(): List<String> = asSequence()
        .map(String::trim)
        .map { it.removePrefix("#").trim() }
        .filter { it.isNotEmpty() && it.length <= Post.MAX_HASHTAG_LENGTH }
        .distinct()
        .toList()

    data class Command(val userId: Long, val instagramUrl: String, val memo: String? = null)

    data class Result(val savedPostId: Long, val postId: Long, val placeParsingStatus: PlaceParsingStatusView)
}
