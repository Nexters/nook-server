package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.port.SaveSharedPostPort
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SharedPostSavePersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
) : SaveSharedPostPort {
    @Transactional
    override fun save(userId: Long, sharedPostId: Long, groupIds: Set<Long>): Long {
        val sharedPost = savedPostRepository.findById(sharedPostId).orElseThrow()
        savedPostRepository.restoreByUserIdAndPostId(userId, sharedPost.postId)
        val existing = savedPostRepository.findByUserIdAndPostId(userId, sharedPost.postId)
        val savedPost = existing ?: savedPostRepository.save(
            UserSavedPostEntity(userId = userId, postId = sharedPost.postId, memo = null),
        )
        val savedPostId = requireNotNull(savedPost.id)
        if (existing == null) {
            initializePlaces(userId, savedPostId, sharedPost.postId)
        }
        addToGroups(savedPostId, groupIds)
        return savedPostId
    }

    private fun initializePlaces(userId: Long, savedPostId: Long, sourcePostId: Long) {
        savedPostPlaceRepository.insertAllFromPost(savedPostId, sourcePostId)
        savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(savedPostId).forEach { place ->
            bookmarkRepository.insertIgnoreWithMemo(userId, place.placeId, memo = null)
        }
    }

    private fun addToGroups(savedPostId: Long, groupIds: Set<Long>) {
        val activeGroupIds = groupPostRepository.findAllByUserSavedPostId(savedPostId)
            .mapTo(mutableSetOf(), GroupPostEntity::groupId)
        val missingGroupIds = (groupIds - activeGroupIds).filter { groupId ->
            groupPostRepository.restore(groupId, savedPostId) == 0
        }
        groupPostRepository.saveAll(missingGroupIds.map { GroupPostEntity(it, savedPostId) })
    }
}
