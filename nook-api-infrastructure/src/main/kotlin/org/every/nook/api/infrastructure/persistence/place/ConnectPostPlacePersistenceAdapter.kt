package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ConnectPostPlacePersistenceAdapter(
    private val savedPostLockRepository: UserSavedPostLockJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
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
        val existingSavedPostPlace = savedPostPlaceRepository.findByUserSavedPostIdAndPlaceId(savedPostId, placeId)
        if (existingSavedPostPlace == null) {
            val nextSequence = savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(savedPostId)
                .maxOfOrNull(UserSavedPostPlaceEntity::sequence)
                ?.plus(1)
                ?: 0
            savedPostPlaceRepository.save(UserSavedPostPlaceEntity(savedPostId, placeId, nextSequence))
        }
        bookmarkRepository.insertIgnoreWithMemo(userId = userId, placeId = placeId, memo = savedPost.memo)
        eventPublisher.publishEvent(PlaceTagsRequestedEvent(savedPost.postId, placeId, candidate))
        return ConnectPostPlacePort.Result.Connected(placeId)
    }

    private fun findSavedPostForConnection(savedPostId: Long, userId: Long) =
        savedPostLockRepository.findByIdAndUserIdForUpdate(savedPostId, userId)

    private companion object {
        val IN_PROGRESS_STATUSES = setOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
    }
}
