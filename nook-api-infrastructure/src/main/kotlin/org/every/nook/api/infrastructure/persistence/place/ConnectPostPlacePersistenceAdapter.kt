package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.context.ApplicationEventPublisher
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
    private val eventPublisher: ApplicationEventPublisher,
) : ConnectPostPlacePort {
    @Transactional
    override fun connect(
        userId: Long,
        savedPostId: Long,
        candidate: PlaceCandidate,
        supplement: PlaceSupplement?,
    ): ConnectPostPlacePort.Result {
        val savedPost = findSavedPostForConnection(savedPostId, userId)
            ?: return ConnectPostPlacePort.Result.PostNotFound
        val parsingJob = parsingJobRepository.findByPostId(savedPost.postId)
        if (parsingJob == null || parsingJob.status in IN_PROGRESS_STATUSES) {
            return ConnectPostPlacePort.Result.ParsingInProgress
        }

        placeRepository.insertIgnore(
            provider = candidate.provider,
            externalPlaceId = candidate.externalPlaceId,
            name = candidate.name,
            address = candidate.address,
            city = candidate.city,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            category = candidate.category,
            phoneNumber = candidate.phoneNumber,
        )
        val place = requireNotNull(
            placeRepository.findByProviderAndExternalPlaceId(candidate.provider, candidate.externalPlaceId),
        )
        supplement?.let(place::updateSupplement)
        val placeId = requireNotNull(place.id)
        val existingPostPlace = postPlaceRepository.findByPostIdAndPlaceId(savedPost.postId, placeId)
        if (existingPostPlace == null) {
            val nextSequence = postPlaceRepository.findAllByPostIdOrderBySequenceAsc(savedPost.postId)
                .maxOfOrNull(PostPlaceEntity::sequence)
                ?.plus(1)
                ?: 0
            postPlaceRepository.save(PostPlaceEntity(savedPost.postId, placeId, nextSequence))
        }
        bookmarkRepository.insertIgnoreWithMemo(userId = userId, placeId = placeId, memo = savedPost.memo)
        if (parsingJob.status == PlaceParsingStatus.FAILED) {
            parsingJob.status = PlaceParsingStatus.COMPLETED
            parsingJob.failureReason = null
        }
        eventPublisher.publishEvent(PlaceTagsRequestedEvent(savedPost.postId, placeId, candidate))
        return ConnectPostPlacePort.Result.Connected(placeId)
    }

    private fun findSavedPostForConnection(savedPostId: Long, userId: Long) =
        savedPostRepository.findByIdAndUserId(savedPostId, userId)
            ?.takeIf { postRepository.findByIdForUpdate(it.postId) != null }

    private companion object {
        val IN_PROGRESS_STATUSES = setOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
    }
}
