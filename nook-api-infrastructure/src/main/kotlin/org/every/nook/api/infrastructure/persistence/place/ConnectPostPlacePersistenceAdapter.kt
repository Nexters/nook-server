package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ConnectPostPlacePersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val postRepository: PostJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val parsingJobRepository: PlaceParsingJobJpaRepository,
) : ConnectPostPlacePort {
    @Transactional
    override fun connect(userId: Long, savedPostId: Long, candidate: PlaceCandidate): ConnectPostPlacePort.Result {
        val savedPost = savedPostRepository.findByIdAndUserId(savedPostId, userId)
            ?: return ConnectPostPlacePort.Result.PostNotFound
        requireNotNull(postRepository.findByIdForUpdate(savedPost.postId))
        val parsingJob = parsingJobRepository.findByPostId(savedPost.postId)
        if (parsingJob == null || parsingJob.status in IN_PROGRESS_STATUSES) {
            return ConnectPostPlacePort.Result.ParsingInProgress
        }

        placeRepository.insertIgnore(
            provider = candidate.provider,
            externalPlaceId = candidate.externalPlaceId,
            name = candidate.name,
            address = candidate.address,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            category = candidate.category,
            phoneNumber = candidate.phoneNumber,
        )
        val place = requireNotNull(
            placeRepository.findByProviderAndExternalPlaceId(candidate.provider, candidate.externalPlaceId),
        )
        val placeId = requireNotNull(place.id)
        val existingPostPlace = postPlaceRepository.findByPostIdAndPlaceId(savedPost.postId, placeId)
        if (existingPostPlace == null) {
            val nextSequence = postPlaceRepository.findAllByPostIdOrderBySequenceAsc(savedPost.postId)
                .maxOfOrNull(PostPlaceEntity::sequence)
                ?.plus(1)
                ?: 0
            postPlaceRepository.save(PostPlaceEntity(savedPost.postId, placeId, nextSequence))
        }
        bookmarkRepository.insertIgnore(userId, placeId)
        if (parsingJob.status == PlaceParsingStatus.FAILED) {
            parsingJob.status = PlaceParsingStatus.COMPLETED
            parsingJob.failureReason = null
        }
        return ConnectPostPlacePort.Result.Connected(placeId)
    }

    private companion object {
        val IN_PROGRESS_STATUSES = setOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
    }
}
