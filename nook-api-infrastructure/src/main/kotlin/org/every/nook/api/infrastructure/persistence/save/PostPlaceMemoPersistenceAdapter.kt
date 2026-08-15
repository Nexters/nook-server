package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.port.UpdatePostPlaceMemoPort
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PostPlaceMemoPersistenceAdapter(
    private val userSavedPostRepository: UserSavedPostJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val memoRepository: UserSavedPostPlaceMemoJpaRepository,
) : UpdatePostPlaceMemoPort {
    @Transactional
    override fun update(userId: Long, postId: Long, placeId: Long, memo: String?): Boolean {
        val savedPost = userSavedPostRepository.findByIdAndUserId(postId, userId) ?: return false
        postPlaceRepository.findByPostIdAndPlaceId(savedPost.postId, placeId) ?: return false
        updateMemo(userId, postId, placeId, memo)
        return true
    }

    private fun updateMemo(userId: Long, postId: Long, placeId: Long, memo: String?) {
        val existingMemo = memoRepository.findByUserSavedPostIdAndPlaceId(postId, placeId)
        if (memo == null) {
            existingMemo?.let(memoRepository::delete)
            return
        }
        if (existingMemo == null) {
            memoRepository.insertIgnore(userId, postId, placeId, memo)
        } else {
            existingMemo.memo = memo
        }
    }
}
