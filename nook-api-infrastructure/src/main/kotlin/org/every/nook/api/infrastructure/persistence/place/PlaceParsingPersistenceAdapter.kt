package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceParsingPersistenceAdapter(
    private val jobRepository: PlaceParsingJobJpaRepository,
    private val postRepository: PostJpaRepository,
    private val hashtagRepository: PostHashtagJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
) : PlaceParsingJobPort {
    @Transactional
    override fun claimNext(): ClaimedPlaceParsingJob? {
        val job = jobRepository.findNextPendingForUpdate() ?: return null
        val post = postRepository.findById(job.postId).orElseThrow()
        job.status = PlaceParsingStatus.PROCESSING
        job.failureReason = null

        return ClaimedPlaceParsingJob(
            postId = job.postId,
            body = post.body,
            hashtags = hashtagRepository.findAllByPostIdOrderBySequenceAsc(job.postId).map { it.hashtag },
            sourceLocationTag = post.sourceLocationTag,
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
                bookmarked = true,
            )
        }
        postPlaceRepository.saveAll(postPlaces)
        job.status = PlaceParsingStatus.COMPLETED
        job.failureReason = null
    }

    @Transactional
    override fun fail(postId: Long, reason: String) {
        val job = requireNotNull(jobRepository.findByPostId(postId))
        job.status = PlaceParsingStatus.FAILED
        job.failureReason = reason.take(FAILURE_REASON_MAX_LENGTH)
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
        const val FAILURE_REASON_MAX_LENGTH = 500
    }
}
