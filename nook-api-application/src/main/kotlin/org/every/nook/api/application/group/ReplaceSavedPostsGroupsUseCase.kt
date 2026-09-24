package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPostManagementPort
import org.every.nook.api.application.post.error.PostNotFoundException

class ReplaceSavedPostsGroupsUseCase(private val groupPostManagementPort: GroupPostManagementPort) {
    operator fun invoke(command: Command) {
        when (
            groupPostManagementPort.replaceAll(
                userId = command.userId,
                savedPostIds = command.savedPostIds.toSet(),
                groupIds = command.groupIds.toSet(),
            )
        ) {
            GroupPostManagementPort.ReplaceResult.Updated -> Unit
            GroupPostManagementPort.ReplaceResult.PostNotFound -> throw PostNotFoundException()
            GroupPostManagementPort.ReplaceResult.GroupNotFound -> throw GroupNotFoundException()
        }
    }

    data class Command(val userId: Long, val savedPostIds: List<Long>, val groupIds: List<Long>)
}
