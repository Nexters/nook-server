package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class GroupPostManagementAdapter(
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : GroupPostManagementPort {
    @Transactional
    override fun replace(userId: Long, savedPostId: Long, groupIds: Set<Long>): GroupPostManagementPort.ReplaceResult {
        if (savedPostRepository.findByIdAndUserId(savedPostId, userId) == null) {
            return GroupPostManagementPort.ReplaceResult.PostNotFound
        }
        if (!ownsAllGroups(userId, groupIds)) {
            return GroupPostManagementPort.ReplaceResult.GroupNotFound
        }

        groupPostRepository.softDeleteAllByUserSavedPostId(savedPostId, clock.instant())
        val newGroupIds = groupIds.filter { groupId -> groupPostRepository.restore(groupId, savedPostId) == 0 }
        groupPostRepository.saveAll(
            newGroupIds.map { groupId ->
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
