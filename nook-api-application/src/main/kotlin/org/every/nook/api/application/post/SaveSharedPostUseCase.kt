package org.every.nook.api.application.post

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.error.SharedResourceNotFoundException
import org.every.nook.api.application.group.port.GroupOwnershipPort
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.application.group.resolveActive
import org.every.nook.api.application.post.port.SaveSharedPostPort

class SaveSharedPostUseCase(
    private val groupSharePort: GroupSharePort,
    private val groupOwnershipPort: GroupOwnershipPort,
    private val saveSharedPostPort: SaveSharedPostPort,
) {
    operator fun invoke(command: Command): Result {
        val groupIds = command.groupIds.toSet()
        validateGroups(command.userId, groupIds)
        val access = groupSharePort.resolveActive(command.shareToken)
        if (!groupSharePort.containsPost(access, command.sharedPostId)) {
            throw SharedResourceNotFoundException()
        }
        return Result(saveSharedPostPort.save(command.userId, command.sharedPostId, groupIds))
    }

    private fun validateGroups(userId: Long, groupIds: Set<Long>) {
        if (groupIds.isEmpty()) {
            throw InvalidGroupException(IllegalArgumentException("At least one group is required"))
        }
        if (!groupOwnershipPort.ownsAll(userId, groupIds)) {
            throw GroupNotFoundException()
        }
    }

    data class Command(val userId: Long, val shareToken: String, val sharedPostId: Long, val groupIds: List<Long>)

    data class Result(val postId: Long)
}
