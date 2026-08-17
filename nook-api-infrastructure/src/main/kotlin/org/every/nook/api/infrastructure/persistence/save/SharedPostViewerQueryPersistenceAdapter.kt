package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.group.port.SharedPostViewerQueryPort
import org.every.nook.api.application.post.model.SavedPostGroup
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SharedPostViewerQueryPersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val groupRepository: GroupJpaRepository,
) : SharedPostViewerQueryPort {
    @Transactional(readOnly = true)
    override fun findViewerGroups(viewerId: Long, sharedSavedPostId: Long): List<SavedPostGroup> {
        val sharedSavedPost = savedPostRepository.findById(sharedSavedPostId).orElse(null) ?: return emptyList()
        val viewerSavedPost = savedPostRepository.findByUserIdAndPostId(viewerId, sharedSavedPost.postId)
            ?: return emptyList()
        val viewerSavedPostId = requireNotNull(viewerSavedPost.id)
        val groupIds = groupPostRepository.findAllByUserSavedPostIdIn(listOf(viewerSavedPostId))
            .map { it.groupId }
        return groupRepository.findAllByIdInAndUserId(groupIds, viewerId).map { group ->
            SavedPostGroup(requireNotNull(group.id), group.name, group.color.name)
        }
    }
}
