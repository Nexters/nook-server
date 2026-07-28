package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.OutstandingPlaceParsingJob
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class PlaceParsingPersistenceAdapter(
    private val jobRepository: PlaceParsingJobJpaRepository,
    private val postRepository: PostJpaRepository,
    private val hashtagRepository: PostHashtagJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val userSavedPostRepository: UserSavedPostJpaRepository,
    private val userPlaceBookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : PlaceParsingJobPort {
    @Transactional
    override fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob? {
        val job = jobRepository.findByPostIdForUpdate(postId) ?: return null
        val now = clock.instant()
        if (!job.isAvailable(now, processingTimeout)) {
            return null
        }
        val post = postRepository.findById(job.postId).orElseThrow()
        job.status = PlaceParsingStatus.PROCESSING
        job.failureReason = null
        job.attemptCount += 1
        job.nextAttemptAt = now

        return ClaimedPlaceParsingJob(
            postId = job.postId,
            attempt = job.attemptCount,
            body = post.body,
            hashtags = hashtagRepository.findAllByPostIdOrderBySequenceAsc(job.postId).map { it.hashtag },
            sourceLocationTag = post.sourceLocationTag,
        )
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
        val postPlaces = distinctPlaces.mapIndexed { sequence, candidate ->
            val place = placeRepository.findByProviderAndExternalPlaceId(
                candidate.provider,
                candidate.externalPlaceId,
            ) ?: placeRepository.save(candidate.toEntity())
            PostPlaceEntity(
                postId = postId,
                placeId = requireNotNull(place.id),
                sequence = sequence,
            )
        }
        postPlaceRepository.saveAll(postPlaces)
        userSavedPostRepository.findDistinctUserIdsByPostId(postId).forEach { userId ->
            postPlaces.forEach { postPlace ->
                userPlaceBookmarkRepository.insertIgnore(userId, postPlace.placeId)
            }
        }
        job.status = PlaceParsingStatus.COMPLETED
        job.failureReason = null
    }

    @Transactional
    override fun retry(postId: Long, nextAttemptAt: Instant) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        check(job.status == PlaceParsingStatus.PROCESSING)
        job.status = PlaceParsingStatus.PENDING
        job.failureReason = null
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
        latitude = latitude,
        longitude = longitude,
        category = category,
        phoneNumber = phoneNumber,
    )

    private companion object {
        val OUTSTANDING_STATUSES = listOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
        const val FAILURE_REASON_MAX_LENGTH = 500
    }
}
