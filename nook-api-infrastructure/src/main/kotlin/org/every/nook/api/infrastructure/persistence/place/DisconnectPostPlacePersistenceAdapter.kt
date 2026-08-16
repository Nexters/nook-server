package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.port.DisconnectPostPlacePort
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DisconnectPostPlacePersistenceAdapter(
    private val savedPostLockRepository: UserSavedPostLockJpaRepository,
    private val savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
) : DisconnectPostPlacePort {
    @Transactional
    override fun disconnect(userId: Long, savedPostId: Long, placeId: Long): DisconnectPostPlacePort.Result {
        savedPostLockRepository.findByIdAndUserIdForUpdate(savedPostId, userId)
            ?: return DisconnectPostPlacePort.Result.POST_NOT_FOUND
        if (savedPostPlaceRepository.deleteByUserSavedPostIdAndPlaceId(savedPostId, placeId) == 0L) {
            return DisconnectPostPlacePort.Result.PLACE_NOT_CONNECTED
        }
        if (savedPostPlaceRepository.existsActiveByUserIdAndPlaceId(userId, placeId) == 0L) {
            bookmarkRepository.deleteByUserIdAndPlaceId(userId, placeId)
        }
        return DisconnectPostPlacePort.Result.DISCONNECTED
    }
}
