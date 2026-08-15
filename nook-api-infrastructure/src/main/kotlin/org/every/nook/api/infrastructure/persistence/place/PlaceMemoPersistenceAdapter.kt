package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.port.UpdatePlaceMemoPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceMemoPersistenceAdapter(private val bookmarkRepository: UserPlaceBookmarkJpaRepository) :
    UpdatePlaceMemoPort {
    @Transactional
    override fun update(userId: Long, placeId: Long, memo: String?): Boolean {
        val bookmark = bookmarkRepository.findByUserIdAndPlaceId(userId, placeId) ?: return false
        bookmark.memo = memo
        return true
    }
}
