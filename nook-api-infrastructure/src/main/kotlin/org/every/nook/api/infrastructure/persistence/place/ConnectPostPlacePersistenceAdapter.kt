package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent
import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ConnectPostPlacePersistenceAdapter(
    private val savedPostLockRepository: UserSavedPostLockJpaRepository,
    private val placeIdentityResolver: PlaceIdentityResolver,
    private val savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val sharedBookmarkSyncRepository: SharedPlaceBookmarkSyncJpaRepository,
    private val parsingJobRepository: PlaceParsingJobJpaRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : ConnectPostPlacePort {
    @Transactional
    override fun connect(userId: Long, savedPostId: Long, candidate: PlaceCandidate): ConnectPostPlacePort.Result {
        val savedPost = findSavedPostForConnection(savedPostId, userId)
            ?: return ConnectPostPlacePort.Result.PostNotFound
        val parsingJob = parsingJobRepository.findByPostId(savedPost.postId)
        if (parsingJob == null || parsingJob.status in IN_PROGRESS_STATUSES) {
            return ConnectPostPlacePort.Result.ParsingInProgress
        }

        val place = placeIdentityResolver.resolve(candidate)
        val shouldRequestThumbnail = place.shouldRequestThumbnailSupplement()
        if (shouldRequestThumbnail) {
            place.updateThumbnailParsing(PlaceThumbnailParsingStatus.PENDING, null)
        }
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
        sharedBookmarkSyncRepository.insertForActiveSubscribers(savedPostId = savedPostId, placeId = placeId)
        if (shouldRequestThumbnail) {
            eventPublisher.publishEvent(
                PlaceThumbnailsRequestedEvent(
                    postId = savedPost.postId,
                    requests = listOf(PlaceThumbnailProvider.Request(candidate, sourcePostId = savedPost.postId)),
                ),
            )
        }
        eventPublisher.publishEvent(PlaceTagsRequestedEvent(savedPost.postId, placeId, candidate))
        return ConnectPostPlacePort.Result.Connected(placeId)
    }

    private fun findSavedPostForConnection(savedPostId: Long, userId: Long) =
        savedPostLockRepository.findByIdAndUserIdForUpdate(savedPostId, userId)

    private companion object {
        val IN_PROGRESS_STATUSES = setOf(PlaceParsingStatus.PENDING, PlaceParsingStatus.PROCESSING)
    }
}
