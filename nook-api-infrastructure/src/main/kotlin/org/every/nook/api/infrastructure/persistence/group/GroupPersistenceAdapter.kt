package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.GroupView
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.GroupColor
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GroupPersistenceAdapter(
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
) : GroupPort {
    @Transactional(readOnly = true)
    override fun findAll(userId: Long): List<GroupView> =
        groupRepository.findAllSummaries(userId).map { projection -> projection.toView() }

    override fun create(userId: Long, name: String, color: GroupColor): GroupView? {
        if (groupRepository.existsByUserIdAndName(userId, name)) {
            return null
        }

        return try {
            val saved = groupRepository.saveAndFlush(GroupEntity(userId = userId, name = name, color = color))
            GroupView(id = requireNotNull(saved.id), name = saved.name, color = saved.color.name, postCount = 0)
        } catch (_: DataIntegrityViolationException) {
            null
        }
    }

    override fun update(userId: Long, groupId: Long, name: String, color: GroupColor): GroupPort.UpdateResult {
        if (!groupRepository.existsByIdAndUserId(groupId, userId)) {
            return GroupPort.UpdateResult.NotFound
        }
        if (groupRepository.existsByUserIdAndNameAndIdNot(userId, name, groupId)) {
            return GroupPort.UpdateResult.DuplicateName
        }

        return try {
            val updated = groupRepository.updateByIdAndUserId(groupId, userId, name, color)
            if (updated == 0) {
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
        } catch (_: DataIntegrityViolationException) {
            GroupPort.UpdateResult.DuplicateName
        }
    }

    @Transactional
    override fun delete(userId: Long, groupId: Long): Boolean = groupRepository.deleteByIdAndUserId(groupId, userId) > 0

    private fun GroupSummaryProjection.toView(): GroupView =
        GroupView(id = id, name = name, color = color.name, postCount = postCount)
}
