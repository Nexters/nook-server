package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.application.post.error.PostNotFoundException

class ReplaceSavedPostGroupsUseCase(private val groupPostManagementPort: GroupPostManagementPort) {
    operator fun invoke(command: Command) {
        when (
            groupPostManagementPort.replace(
                userId = command.userId,
                savedPostId = command.savedPostId,
                groupIds = command.groupIds.toSet(),
            )
        ) {
            GroupPostManagementPort.ReplaceResult.Updated -> Unit
            GroupPostManagementPort.ReplaceResult.PostNotFound -> throw PostNotFoundException()
            GroupPostManagementPort.ReplaceResult.GroupNotFound -> throw GroupNotFoundException()
        }
    }

    data class Command(val userId: Long, val savedPostId: Long, val groupIds: List<Long>)
}
