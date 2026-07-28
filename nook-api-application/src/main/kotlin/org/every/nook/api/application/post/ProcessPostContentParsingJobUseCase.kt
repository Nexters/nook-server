package org.every.nook.api.application.post

import mu.KotlinLogging
import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.Post
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ProcessPostContentParsingJobUseCase(
    private val jobPort: PostContentParsingJobPort,
    private val extractPostContent: ExtractPostContentUseCase,
    private val titleGenerator: PostTitleGenerator,
    private val mediaStorage: PostMediaStoragePort,
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long): Result {
        val job = jobPort.claim(postId, processingTimeout) ?: return Result.Skipped
        val startedAt = clock.instant()
        logger.info { "Post content parsing started: postId=${job.postId}, attempt=${job.attempt}" }

        return runCatching {
            val extracted = extractPostContent(job.canonicalUrl)
            val providedPost = extracted.post.copy(
                sourceLocationTag = extracted.toSourceLocationTag(),
                hashtags = extracted.hashtags.toPersistentHashtags(),
            )
            val storedPost = providedPost.copy(
                title = titleGenerator.generate(
                    PostTitleGenerator.Request(
                        body = providedPost.body,
                        hashtags = providedPost.hashtags,
                        sourceLocationTag = providedPost.sourceLocationTag,
                    ),
                ),
                media = providedPost.media.map(mediaStorage::store),
            )
            jobPort.complete(job.postId, storedPost)
            val duration = Duration.between(startedAt, clock.instant()).toMillis()
            logger.info {
                "Post content parsing completed: postId=${job.postId}, attempt=${job.attempt}, " +
                    "mediaCount=${storedPost.media.size}, durationMs=$duration"
            }
            Result.Completed
        }.getOrElse { exception ->
            handleFailure(job, exception, startedAt)
        }
    }

    private fun handleFailure(job: ClaimedPostContentParsingJob, exception: Throwable, startedAt: Instant): Result {
        val reason = exception.message.orEmpty()
            .ifBlank { DEFAULT_FAILURE_REASON }
            .take(MAX_FAILURE_REASON_LENGTH)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        val backoff = retryBackoffs.getOrNull(job.attempt - 1)
        if (backoff != null) {
            val nextAttemptAt = clock.instant().plus(backoff)
            jobPort.retry(job.postId, nextAttemptAt, reason)
            logger.warn(exception) {
                "Post content parsing retry scheduled: postId=${job.postId}, attempt=${job.attempt}, " +
                    "nextAttemptAt=$nextAttemptAt, durationMs=$duration, reason=$reason"
            }
            return Result.Retry(nextAttemptAt)
        }

        jobPort.fail(job.postId, reason)
        logger.error(exception) {
            "Post content parsing failed permanently: postId=${job.postId}, attempt=${job.attempt}, " +
                "durationMs=$duration, reason=$reason"
        }
        return Result.Failed
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

    sealed interface Result {
        data object Completed : Result

        data class Retry(val nextAttemptAt: Instant) : Result

        data object Failed : Result

        data object Skipped : Result
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val MAX_FAILURE_REASON_LENGTH = 500
        const val DEFAULT_FAILURE_REASON = "Post content parsing failed"
    }
}
