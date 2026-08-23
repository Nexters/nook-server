package org.every.nook.api.application.post

import mu.KotlinLogging
import org.every.nook.api.application.content.ExtractPostContentUseCase
import org.every.nook.api.application.content.ExtractedPostContent
import org.every.nook.api.application.content.PostContentNotFoundException
import org.every.nook.api.application.content.UnsupportedPostUrlException
import org.every.nook.api.application.place.ImageTextExtractor
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.NoOpProcessingTracePort
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.ProcessingTraceEvent
import org.every.nook.api.application.processing.ProcessingTracePort
import org.every.nook.api.application.processing.error
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.measure
import org.every.nook.api.application.processing.warn
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

// Trace/fencing helpers stay in this orchestration boundary to preserve stage and attempt transition order.
@Suppress("LongMethod", "TooManyFunctions")
class ProcessPostContentParsingJobUseCase(
    private val jobPort: PostContentParsingJobPort,
    private val extractPostContent: ExtractPostContentUseCase,
    private val contentInference: PostContentInference,
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val imageTextExtractor: ImageTextExtractor = ImageTextExtractor { emptyList() },
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val tracePort: ProcessingTracePort = NoOpProcessingTracePort,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long): Result {
        val job = jobPort.claim(postId, processingTimeout) ?: return Result.Skipped
        val startedAt = clock.instant()
        eventLogger.info(job.event("content.job.claimed", JOB_STAGE, SUCCESS_OUTCOME))
        recordTrace(job, JOB_STAGE, "content.job.claimed", SUCCESS_OUTCOME)
        logger.info { "Post content parsing started: postId=${job.postId}, attempt=${job.attempt}" }

        return runCatching {
            ensureActive(job, ParsingProgressStage.CONTENT_FETCH)
            val extracted = measure(job, EXTRACT_STAGE) {
                extractPostContent(job.canonicalUrl)
            }
            val providedPost = extracted.post.copy(
                sourceLocationTag = extracted.toSourceLocationTag(),
                hashtags = extracted.hashtags.toPersistentHashtags(),
            )
            val coverTranscript = providedPost.coverImageUrl()?.let { imageUrl ->
                ensureActive(job, ParsingProgressStage.CONTENT_COVER_TITLE)
                measure(job, COVER_TITLE_STAGE) {
                    runCatching {
                        imageTextExtractor.extract(
                            ImageTextExtractor.Request(listOf(ImageTextExtractor.ImageInput(1, imageUrl))),
                        ).firstOrNull { it.imageIndex == 1 }
                    }.onFailure { exception ->
                        logger.warn(exception) {
                            "Cover OCR failed; falling back to text title: postId=${job.postId}"
                        }
                    }.getOrNull()
                }
            }
            ensureActive(job, ParsingProgressStage.CONTENT_INFERENCE)
            val inference = measure(job, INFERENCE_STAGE) {
                contentInference.infer(
                    PostContentInference.Request(
                        body = providedPost.body,
                        hashtags = providedPost.hashtags,
                        sourceLocationTag = providedPost.sourceLocationTag,
                    ),
                )
            }
            recordTrace(
                job,
                INFERENCE_STAGE,
                "content.inference.result",
                SUCCESS_OUTCOME,
                details = mapOf("placeClueCount" to inference.placeClues.size.toString()),
            )
            val completedPost = providedPost.copy(
                title = null,
            )
            ensureActive(job, ParsingProgressStage.CONTENT_SAVE)
            val stored = measure(job, COMPLETE_STAGE) {
                jobPort.complete(
                    postId = job.postId,
                    attempt = job.attempt,
                    post = completedPost,
                    textPlaceClues = inference.placeClues,
                    imageTranscripts = listOfNotNull(coverTranscript),
                    sourceProfileHints = extracted.sourceProfileHints,
                )
            }
            if (stored) completed(job, completedPost, startedAt) else Result.Skipped
        }.getOrElse { exception ->
            handleFailure(job, exception, startedAt)
        }
    }

    private fun completed(job: ClaimedPostContentParsingJob, post: Post, startedAt: Instant): Result {
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        logger.info {
            "Post content parsing completed: postId=${job.postId}, attempt=${job.attempt}, " +
                "mediaCount=${post.media.size}, durationMs=$duration"
        }
        eventLogger.info(
            job.event(
                action = "content.job.completed",
                stage = JOB_STAGE,
                outcome = SUCCESS_OUTCOME,
                durationMs = duration,
                fields = mapOf("content.media_count" to post.media.size),
            ),
        )
        recordTrace(
            job,
            JOB_STAGE,
            "content.job.completed",
            SUCCESS_OUTCOME,
            duration,
            mapOf("mediaCount" to post.media.size.toString()),
        )
        return Result.Completed
    }

    private fun <T> measure(job: ClaimedPostContentParsingJob, stage: String, action: () -> T): T {
        val startedAt = clock.instant()
        return runCatching {
            metrics.measure(CONTENT_FLOW, stage, job.postId, job.attempt, clock, action)
        }.onSuccess {
            recordTrace(job, stage, "content.stage.completed", SUCCESS_OUTCOME, elapsedMillis(startedAt))
        }.onFailure { exception ->
            recordTrace(
                job,
                stage,
                "content.stage.failed",
                FAILURE_OUTCOME,
                elapsedMillis(startedAt),
                mapOf("reason" to exception.message.orEmpty().take(MAX_FAILURE_REASON_LENGTH)),
            )
        }.getOrThrow()
    }

    // Each early return represents a distinct fenced state transition and prevents stale attempts from logging errors.
    @Suppress("ReturnCount")
    private fun handleFailure(job: ClaimedPostContentParsingJob, exception: Throwable, startedAt: Instant): Result {
        if (exception is StalePostContentParsingAttemptException) return Result.Skipped
        val reason = exception.message.orEmpty()
            .ifBlank { DEFAULT_FAILURE_REASON }
            .take(MAX_FAILURE_REASON_LENGTH)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        if (exception is PostContentNotFoundException || exception is UnsupportedPostUrlException) {
            if (!jobPort.fail(job.postId, job.attempt, reason)) return Result.Skipped
            eventLogger.warn(
                job.event("content.job.failed", JOB_STAGE, FAILURE_OUTCOME, duration, failureFields(exception, reason)),
                exception,
            )
            logger.warn {
                "Post content parsing failed without retry: postId=${job.postId}, attempt=${job.attempt}, " +
                    "durationMs=$duration, reason=$reason"
            }
            recordTrace(job, JOB_STAGE, "content.job.failed", FAILURE_OUTCOME, duration, mapOf("reason" to reason))
            return Result.Failed
        }
        val backoff = retryBackoffs.getOrNull(job.attempt - 1)
        if (backoff != null) {
            val nextAttemptAt = clock.instant().plus(backoff)
            if (!jobPort.retry(job.postId, job.attempt, nextAttemptAt, reason)) return Result.Skipped
            eventLogger.warn(
                job.event(
                    "content.job.retry_scheduled",
                    JOB_STAGE,
                    FAILURE_OUTCOME,
                    duration,
                    failureFields(exception, reason) + ("retry.next_attempt_at" to nextAttemptAt),
                ),
                exception,
            )
            logger.warn(exception) {
                "Post content parsing retry scheduled: postId=${job.postId}, attempt=${job.attempt}, " +
                    "nextAttemptAt=$nextAttemptAt, durationMs=$duration, reason=$reason"
            }
            recordTrace(
                job,
                JOB_STAGE,
                "content.job.retry_scheduled",
                FAILURE_OUTCOME,
                duration,
                mapOf("reason" to reason, "nextAttemptAt" to nextAttemptAt.toString()),
            )
            return Result.Retry(nextAttemptAt)
        }

        if (!jobPort.fail(job.postId, job.attempt, reason)) return Result.Skipped
        eventLogger.error(
            job.event("content.job.failed", JOB_STAGE, FAILURE_OUTCOME, duration, failureFields(exception, reason)),
            exception,
        )
        logger.error(exception) {
            "Post content parsing failed permanently: postId=${job.postId}, attempt=${job.attempt}, " +
                "durationMs=$duration, reason=$reason"
        }
        recordTrace(job, JOB_STAGE, "content.job.failed", FAILURE_OUTCOME, duration, mapOf("reason" to reason))
        return Result.Failed
    }

    private fun ensureActive(job: ClaimedPostContentParsingJob, stage: ParsingProgressStage) {
        if (!jobPort.updateProgress(job.postId, job.attempt, stage)) throw StalePostContentParsingAttemptException()
    }

    private class StalePostContentParsingAttemptException : RuntimeException()

    private fun elapsedMillis(startedAt: Instant): Long = Duration.between(startedAt, clock.instant()).toMillis()

    private fun recordTrace(
        job: ClaimedPostContentParsingJob,
        stage: String,
        action: String,
        outcome: String,
        durationMs: Long? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        runCatching {
            tracePort.record(
                ProcessingTraceEvent(
                    job.postId,
                    CONTENT_FLOW,
                    stage,
                    action,
                    outcome,
                    job.attempt,
                    durationMs,
                    details,
                ),
            )
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to store content parsing trace: postId=${job.postId}" }
        }
    }

    private fun ExtractedPostContent.toSourceLocationTag(): String? = sourceLocationNames
        .asSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() && it.length <= Post.MAX_SOURCE_LOCATION_TAG_LENGTH }

    private fun Post.coverImageUrl(): String? = media
        .asSequence()
        .filter { it.type == PostMedia.MediaType.IMAGE }
        .minByOrNull(PostMedia::sequence)
        ?.url

    private fun List<String>.toPersistentHashtags(): List<String> = asSequence()
        .map(String::trim)
        .map { it.removePrefix("#").trim() }
        .filter { it.isNotEmpty() && it.length <= Post.MAX_HASHTAG_LENGTH }
        .distinct()
        .toList()

    private fun ClaimedPostContentParsingJob.event(
        action: String,
        stage: String,
        outcome: String,
        durationMs: Long? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = ProcessingLogEvent(action, CONTENT_FLOW, stage, outcome, postId, attempt, durationMs, fields)

    private fun failureFields(exception: Throwable, reason: String): Map<String, Any?> = mapOf(
        "failure.type" to exception::class.simpleName,
        "failure.reason" to reason,
    )

    sealed interface Result {
        data object Completed : Result

        data class Retry(val nextAttemptAt: Instant) : Result

        data object Failed : Result

        data object Skipped : Result
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val eventLogger = LoggerFactory.getLogger(ProcessPostContentParsingJobUseCase::class.java)
        const val MAX_FAILURE_REASON_LENGTH = 500
        const val DEFAULT_FAILURE_REASON = "Post content parsing failed"
        const val CONTENT_FLOW = "post-content"
        const val EXTRACT_STAGE = "extract"
        const val COVER_TITLE_STAGE = "cover-title"
        const val INFERENCE_STAGE = "inference"
        const val COMPLETE_STAGE = "complete"
        const val JOB_STAGE = "job"
        const val SUCCESS_OUTCOME = "success"
        const val FAILURE_OUTCOME = "failure"
    }
}
