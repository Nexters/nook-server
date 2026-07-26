package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceBookmarkPersistenceAdapter(private val bookmarkRepository: UserPlaceBookmarkJpaRepository) :
    UpdatePlaceBookmarkPort {
    @Transactional
    override fun update(userId: Long, placeId: Long, bookmarked: Boolean): Boolean {
        if (!bookmarkRepository.isAccessible(userId, placeId)) {
            return false
        }

        if (bookmarked) {
            bookmarkRepository.insertIgnore(userId, placeId)
        } else {
            bookmarkRepository.deleteByUserIdAndPlaceId(userId, placeId)
        }
        return true
    }
}
