package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.InferredPlaceTag
import org.every.nook.api.application.place.OutstandingPlaceParsingJob
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceTagSource
import org.every.nook.api.application.place.PlaceTagSourcePort
import org.every.nook.api.application.place.PlaceTagUpdatePort
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailUpdatePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class PlaceParsingPersistenceAdapter(
    private val jobRepository: PlaceParsingJobJpaRepository,
    private val postRepository: PostJpaRepository,
    private val hashtagRepository: PostHashtagJpaRepository,
    private val mediaRepository: PostMediaJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val userSavedPostLockRepository: UserSavedPostLockJpaRepository,
    private val userSavedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    private val userPlaceBookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val postPlaceTagRepository: PostPlaceTagJpaRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : PlaceParsingJobPort,
    PlaceThumbnailUpdatePort,
    PlaceTagSourcePort,
    PlaceTagUpdatePort {
    @Transactional
    override fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob? {
        val job = jobRepository.findByPostIdForUpdate(postId) ?: return null
        val now = clock.instant()
        if (!job.isAvailable(now, processingTimeout)) {
            return null
        }
        val post = postRepository.findById(job.postId).orElseThrow()
        job.status = PlaceParsingStatus.PROCESSING
        job.attemptCount += 1
        job.nextAttemptAt = now

        return ClaimedPlaceParsingJob(
            postId = job.postId,
            attempt = job.attemptCount,
            body = post.body,
            hashtags = hashtagRepository.findAllByPostIdOrderBySequenceAsc(job.postId).map { it.hashtag },
            sourceLocationTag = post.sourceLocationTag,
            imageUrls = mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(
                job.postId,
                PostMedia.MediaType.IMAGE,
            ).map { it.mediaUrl },
            textClues = job.textPlaceClues?.let { objectMapper.readValue<List<PlaceClue>>(it) },
            imageTranscripts = job.imageTranscripts?.let { objectMapper.readValue<List<ImageTranscript>>(it) },
        )
    }

    @Transactional
    override fun storeImageTranscripts(postId: Long, transcripts: List<ImageTranscript>) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        check(job.status == PlaceParsingStatus.PROCESSING)
        job.imageTranscripts = objectMapper.writeValueAsString(transcripts)
    }

    @Transactional(readOnly = true)
    override fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob> =
        jobRepository.findAllByStatusIn(OUTSTANDING_STATUSES).map { job ->
            OutstandingPlaceParsingJob(
                postId = job.postId,
                availableAt = when (job.status) {
                    PlaceParsingStatus.PENDING -> job.nextAttemptAt
                    PlaceParsingStatus.PROCESSING -> job.updatedAt.plus(processingTimeout)
                    else -> error("Unexpected place parsing status: ${job.status}")
                },
            )
        }

    @Transactional
    override fun complete(postId: Long, places: List<PlaceCandidate>) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        check(job.status == PlaceParsingStatus.PROCESSING)
        val distinctPlaces = places.distinctBy { it.provider to it.externalPlaceId }
        val resolvedPlaces = distinctPlaces.map { candidate ->
            val place = placeRepository.findByProviderAndExternalPlaceId(
                candidate.provider,
                candidate.externalPlaceId,
            ) ?: placeRepository.save(candidate.toEntity())
            candidate.copy(googlePlaceId = place.googlePlaceId) to place
        }
        val postPlaces = resolvedPlaces.mapIndexed { sequence, (candidate, place) ->
            PostPlaceEntity(
                postId = postId,
                placeId = requireNotNull(place.id),
                sequence = sequence,
                sourceMediaSequence = candidate.sourceMediaSequence,
            )
        }
        postPlaceRepository.saveAll(postPlaces)
        userSavedPostLockRepository.findAllByPostIdForUpdate(postId).forEach { savedPost ->
            val savedPostId = requireNotNull(savedPost.id)
            userSavedPostPlaceRepository.insertAllFromPost(savedPostId, postId)
            userSavedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(savedPostId).forEach { place ->
                userPlaceBookmarkRepository.insertIgnoreWithMemo(
                    userId = savedPost.userId,
                    placeId = place.placeId,
                    memo = savedPost.memo,
                )
            }
        }
        job.status = PlaceParsingStatus.COMPLETED
        job.failureReason = null
        resolvedPlaces.map { it.first }.zip(postPlaces).forEach { (place, postPlace) ->
            eventPublisher.publishEvent(
                PlaceThumbnailRequestedEvent(
                    postId = postId,
                    place = place,
                    sourceMediaSequence = postPlace.sourceMediaSequence ?: postPlace.sequence,
                    availableAt = clock.instant(),
                ),
            )
            eventPublisher.publishEvent(PlaceTagsRequestedEvent(postId, postPlace.placeId, place))
        }
    }

    @Transactional
    override fun update(
        provider: String,
        externalPlaceId: String,
        status: PlaceThumbnailParsingStatus,
        supplement: PlaceSupplement?,
    ) {
        placeRepository.findByProviderAndExternalPlaceId(provider, externalPlaceId)
            ?.updateThumbnailParsing(status, supplement)
    }

    @Transactional(readOnly = true)
    override fun find(postId: Long): PlaceTagSource? {
        val post = postRepository.findById(postId).orElse(null) ?: return null
        return PlaceTagSource(
            body = post.body,
            hashtags = hashtagRepository.findAllByPostIdOrderBySequenceAsc(postId).map { it.hashtag },
            imageUrls = mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(
                postId,
                PostMedia.MediaType.IMAGE,
            ).map { it.mediaUrl },
        )
    }

    @Transactional
    override fun replace(postId: Long, placeId: Long, tags: List<InferredPlaceTag>) {
        check(
            postPlaceRepository.findByPostIdAndPlaceId(postId, placeId) != null ||
                userSavedPostPlaceRepository.existsByPostIdAndPlaceId(postId, placeId) != 0L,
        ) {
            "Post place relation does not exist"
        }
        postPlaceTagRepository.deleteAllByPostIdAndPlaceId(postId, placeId)
        postPlaceTagRepository.saveAll(tags.map { it.toEntity(postId, placeId) })
        placeRepository.findById(placeId).orElseThrow().updateRepresentativeTags(
            postPlaceTagRepository.findRepresentativeTags(placeId).take(MAX_REPRESENTATIVE_TAG_COUNT),
        )
    }

    @Transactional
    override fun retry(postId: Long, nextAttemptAt: Instant, reason: String) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        check(job.status == PlaceParsingStatus.PROCESSING)
        job.status = PlaceParsingStatus.PENDING
        job.failureReason = reason.take(FAILURE_REASON_MAX_LENGTH)
        job.nextAttemptAt = nextAttemptAt
    }

    @Transactional
    override fun fail(postId: Long, reason: String) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        job.status = PlaceParsingStatus.FAILED
        job.failureReason = reason.take(FAILURE_REASON_MAX_LENGTH)
    }

    private fun PlaceParsingJobEntity.isAvailable(now: Instant, processingTimeout: Duration): Boolean = when (status) {
        PlaceParsingStatus.PENDING -> !nextAttemptAt.isAfter(now)

        PlaceParsingStatus.PROCESSING -> !updatedAt.plus(processingTimeout).isAfter(now)

        PlaceParsingStatus.COMPLETED,
        PlaceParsingStatus.FAILED,
        -> false
    }

    private fun PlaceCandidate.toEntity(): PlaceEntity = PlaceEntity(
        provider = provider,
        externalPlaceId = externalPlaceId,
        name = name,
        address = address,
        city = city,
        latitude = latitude,
        longitude = longitude,
        category = category,
        phoneNumber = phoneNumber,
        googlePlaceId = googlePlaceId,
    )

    private companion object {
        val OUTSTANDING_STATUSES = listOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
        const val FAILURE_REASON_MAX_LENGTH = 500
        const val MAX_REPRESENTATIVE_TAG_COUNT = 4
    }
}
