package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.port.DeleteSavedPostPort
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class SavedPostDeletionAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : DeleteSavedPostPort {
    @Transactional
    override fun delete(userId: Long, savedPostId: Long): Boolean {
        val savedPost = savedPostRepository.findByIdAndUserId(savedPostId, userId) ?: return false
        val now = clock.instant()
        groupPostRepository.softDeleteAllByUserSavedPostId(savedPostId, now)
        savedPost.softDelete(now)
        return true
    }
}
