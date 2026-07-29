package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GroupPersistenceAdapter(
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
) : GroupPort,
    GroupOwnershipPort {
    @Transactional(readOnly = true)
    override fun findAll(userId: Long): List<GroupView> {
        val summaries = groupRepository.findAllSummaries(userId)
        if (summaries.isEmpty()) {
            return emptyList()
        }
        val thumbnailUrlsByGroupId = groupRepository.findRecentThumbnailUrls(userId)
            .groupBy(GroupThumbnailProjection::groupId, GroupThumbnailProjection::thumbnailUrl)
        return summaries.map { projection ->
            projection.toView(thumbnailUrlsByGroupId.getOrDefault(projection.id, emptyList()))
        }
    }

    override fun create(userId: Long, name: String, color: GroupColor): GroupView {
        val saved = groupRepository.saveAndFlush(GroupEntity(userId = userId, name = name, color = color))
        return GroupView(id = requireNotNull(saved.id), name = saved.name, color = saved.color.name, postCount = 0)
    }

    override fun update(userId: Long, groupId: Long, name: String, color: GroupColor): GroupPort.UpdateResult {
        if (!groupRepository.existsByIdAndUserId(groupId, userId)) {
            return GroupPort.UpdateResult.NotFound
        }
        val updated = groupRepository.updateByIdAndUserId(groupId, userId, name, color)
        return if (updated == 0) {
            GroupPort.UpdateResult.NotFound
        } else {
            GroupPort.UpdateResult.Updated(
                GroupView(
                    id = groupId,
                    name = name,
                    color = color.name,
                    postCount = groupPostRepository.countByGroupId(groupId),
                ),
            )
        }
    }

    @Transactional
    override fun delete(userId: Long, groupId: Long): Boolean {
        if (!groupRepository.existsByIdAndUserId(groupId, userId)) {
            return false
        }
        groupPostRepository.deleteAllByGroupId(groupId)
        return groupRepository.deleteByIdAndUserId(groupId, userId) > 0
    }

    @Transactional(readOnly = true)
    override fun ownsAll(userId: Long, groupIds: Set<Long>): Boolean {
        if (groupIds.isEmpty()) {
            return false
        }
        val ownedGroupIds = groupRepository.findAllByUserIdAndIdIn(userId, groupIds)
            .mapTo(mutableSetOf()) { requireNotNull(it.id) }
        return ownedGroupIds == groupIds
    }

    private fun GroupSummaryProjection.toView(thumbnailUrls: List<String>): GroupView = GroupView(
        id = id,
        name = name,
        color = color.name,
        postCount = postCount,
        thumbnailUrls = thumbnailUrls,
    )
}
