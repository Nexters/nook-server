package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GroupPostManagementAdapter(
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val savedPostRepository: UserSavedPostJpaRepository,
) : GroupPostManagementPort {
    @Transactional
    override fun replace(userId: Long, savedPostId: Long, groupIds: Set<Long>): GroupPostManagementPort.ReplaceResult {
        if (savedPostRepository.findByIdAndUserId(savedPostId, userId) == null) {
            return GroupPostManagementPort.ReplaceResult.PostNotFound
        }
        if (!ownsAllGroups(userId, groupIds)) {
            return GroupPostManagementPort.ReplaceResult.GroupNotFound
        }

        groupPostRepository.deleteAllByUserSavedPostId(savedPostId)
        groupPostRepository.saveAll(
            groupIds.map { groupId ->
                GroupPostEntity(
                    groupId = groupId,
                    userSavedPostId = savedPostId,
                )
            },
        )
        return GroupPostManagementPort.ReplaceResult.Updated
    }

    private fun ownsAllGroups(userId: Long, groupIds: Set<Long>): Boolean {
        if (groupIds.isEmpty()) {
            return true
        }
        val ownedGroupIds = groupRepository.findAllByUserIdAndIdIn(userId, groupIds)
            .mapTo(mutableSetOf()) { requireNotNull(it.id) }
        return ownedGroupIds == groupIds
    }
}
