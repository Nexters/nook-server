package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.port.SaveSharedPostPort
import org.every.nook.api.application.post.port.SavedPostQueryPort
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
    private val queryPort: SavedPostQueryPort,
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
            // Legacy archive sharing can save an unfinished source; keep its initial worker population.
            savedPost.placeLinksFrozen = queryPort.findDetail(sharedPost.userId, sharedPostId)
                ?.processingStatus == PostProcessingStatusView.COMPLETED
            initializePlaces(userId, savedPostId, sharedPostId)
        }
        addToGroups(savedPostId, groupIds)
        return savedPostId
    }

    private fun initializePlaces(userId: Long, savedPostId: Long, sourceSavedPostId: Long) {
        val sourcePlaces = savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(sourceSavedPostId)
        savedPostPlaceRepository.saveAll(
            sourcePlaces.map { UserSavedPostPlaceEntity(savedPostId, it.placeId, it.sequence, it.thumbnailUrl) },
        )
        savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(savedPostId).forEach { place ->
            bookmarkRepository.insertOrTouchWithMemo(userId, place.placeId, memo = null)
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
