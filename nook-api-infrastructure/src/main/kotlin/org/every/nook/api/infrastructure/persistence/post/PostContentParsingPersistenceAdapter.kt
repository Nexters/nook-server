package org.every.nook.api.infrastructure.persistence.post

import org.every.nook.api.application.content.SourceProfileHint
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.post.ClaimedPostContentParsingJob
import org.every.nook.api.application.post.OutstandingPostContentParsingJob
import org.every.nook.api.application.post.PostContentParsingJobPort
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class PostContentParsingPersistenceAdapter(
    private val jobRepository: PostContentParsingJobJpaRepository,
    private val postRepository: PostJpaRepository,
    private val mediaRepository: PostMediaJpaRepository,
    private val hashtagRepository: PostHashtagJpaRepository,
    private val placeParsingJobRepository: PlaceParsingJobJpaRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : PostContentParsingJobPort {
    @Transactional
    override fun claim(postId: Long, processingTimeout: Duration): ClaimedPostContentParsingJob? {
        val job = jobRepository.findByPostIdForUpdate(postId) ?: return null
        val now = clock.instant()
        if (!job.isAvailable(now, processingTimeout)) {
            return null
        }
        val post = postRepository.findById(job.postId).orElseThrow()
        job.status = PostContentParsingStatus.PROCESSING
        job.attemptCount += 1
        job.nextAttemptAt = now
        job.resumeProgress(now)
        return ClaimedPostContentParsingJob(
            postId = job.postId,
            attempt = job.attemptCount,
            canonicalUrl = post.canonicalUrl,
        )
    }

    @Transactional(readOnly = true)
    override fun findOutstanding(processingTimeout: Duration): List<OutstandingPostContentParsingJob> =
        jobRepository.findAllByStatusIn(OUTSTANDING_STATUSES).map { job ->
            OutstandingPostContentParsingJob(
                postId = job.postId,
                availableAt = when (job.status) {
                    PostContentParsingStatus.PENDING -> job.nextAttemptAt
                    PostContentParsingStatus.PROCESSING -> job.updatedAt.plus(processingTimeout)
                    else -> error("Unexpected post content parsing status: ${job.status}")
                },
            )
        }

    @Transactional
    override fun updateProgress(postId: Long, stage: ParsingProgressStage) {
        requireNotNull(jobRepository.findByPostId(postId)).advanceProgress(stage, clock.instant())
    }

    @Transactional
    override fun complete(
        postId: Long,
        post: Post,
        textPlaceClues: List<PlaceClue>,
        imageTranscripts: List<ImageTranscript>,
        sourceProfileHints: List<SourceProfileHint>,
    ) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        check(job.status == PostContentParsingStatus.PROCESSING)
        val entity = postRepository.findById(postId).orElseThrow()
        if (!entity.contentManuallyOverridden) {
            entity.updateContent(post)
            mediaRepository.deleteAllByPostId(postId)
            hashtagRepository.deleteAllByPostId(postId)
            mediaRepository.saveAll(
                post.media.map { media ->
                    PostMediaEntity(
                        postId = postId,
                        mediaType = media.type,
                        mediaUrl = media.url,
                        sequence = media.sequence,
                        thumbnailUrl = media.thumbnailUrl,
                    )
                },
            )
            hashtagRepository.saveAll(
                post.hashtags.mapIndexed { sequence, hashtag ->
                    PostHashtagEntity(
                        postId = postId,
                        hashtag = hashtag,
                        sequence = sequence,
                    )
                },
            )
        }
        job.status = PostContentParsingStatus.COMPLETED
        job.failureReason = null
        job.progressPercent = CONTENT_COMPLETED_PERCENT

        if (placeParsingJobRepository.findByPostId(postId) == null) {
            placeParsingJobRepository.save(
                PlaceParsingJobEntity(
                    postId = postId,
                    textPlaceClues = objectMapper.writeValueAsString(textPlaceClues),
                    imageTranscripts = imageTranscripts.toJsonOrNull(),
                    sourceProfileHints = sourceProfileHints.toJsonOrNull(),
                    status = PlaceParsingStatus.PENDING,
                    nextAttemptAt = clock.instant(),
                ),
            )
            eventPublisher.publishEvent(PlaceParsingJobRequestedEvent(postId, clock.instant()))
        }
        if (!entity.contentManuallyOverridden) {
            post.media.forEach { media ->
                eventPublisher.publishEvent(
                    PostMediaStorageRequestedEvent(
                        postId = postId,
                        mediaType = media.type.name,
                        sourceUrl = media.url,
                        sequence = media.sequence,
                        sourceThumbnailUrl = media.thumbnailUrl,
                        availableAt = clock.instant(),
                    ),
                )
            }
        }
    }

    @Transactional
    override fun retry(postId: Long, nextAttemptAt: Instant, reason: String) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        check(job.status == PostContentParsingStatus.PROCESSING)
        job.freezeProgress(clock.instant())
        job.status = PostContentParsingStatus.PENDING
        job.failureReason = reason.take(PostContentParsingJobEntity.FAILURE_REASON_MAX_LENGTH)
        job.nextAttemptAt = nextAttemptAt
    }

    @Transactional
    override fun fail(postId: Long, reason: String) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        job.freezeProgress(clock.instant())
        job.status = PostContentParsingStatus.FAILED
        job.failureReason = reason.take(PostContentParsingJobEntity.FAILURE_REASON_MAX_LENGTH)
    }

    private fun PostContentParsingJobEntity.isAvailable(now: Instant, processingTimeout: Duration): Boolean =
        when (status) {
            PostContentParsingStatus.PENDING -> !nextAttemptAt.isAfter(now)

            PostContentParsingStatus.PROCESSING -> !updatedAt.plus(processingTimeout).isAfter(now)

            PostContentParsingStatus.COMPLETED,
            PostContentParsingStatus.FAILED,
            -> false
        }

    private fun <T> List<T>.toJsonOrNull(): String? = takeIf(List<T>::isNotEmpty)
        ?.let(objectMapper::writeValueAsString)

    private companion object {
        val OUTSTANDING_STATUSES = listOf(
            PostContentParsingStatus.PENDING,
            PostContentParsingStatus.PROCESSING,
        )
        const val CONTENT_COMPLETED_PERCENT = 45
    }
}
