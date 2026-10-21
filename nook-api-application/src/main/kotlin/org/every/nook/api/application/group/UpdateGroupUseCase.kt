package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.application.group.port.GroupSubscribersPort
import org.every.nook.api.domain.group.Group
import org.every.nook.api.domain.group.GroupColor

class UpdateGroupUseCase(private val groupPort: GroupPort, private val subscribersPort: GroupSubscribersPort) {
    operator fun invoke(command: Command): GroupView {
        val name = command.name.trim()
        val color = parseColor(command.color)
        val description = command.description?.trim()
        validateGroup(command.userId, command.groupId, name, color, description)
        return when (val result = groupPort.update(command.userId, command.groupId, name, color, description)) {
            is GroupPort.UpdateResult.Updated -> result.group.withSubscribers(
                subscribersPort.findSummaries(setOf(command.groupId))[command.groupId],
            )

            GroupPort.UpdateResult.NotFound -> throw GroupNotFoundException()
        }
    }

    data class Command(
        val userId: Long,
        val groupId: Long,
        val name: String,
        val color: String,
        val description: String? = null,
    )

    private fun validateGroup(userId: Long, groupId: Long, name: String, color: GroupColor, description: String?) {
        try {
            Group(userId = userId, name = name, color = color, id = groupId, description = description)
        } catch (exception: IllegalArgumentException) {
            throw InvalidGroupException(exception)
        }
    }

    private fun parseColor(color: String): GroupColor = try {
        GroupColor.valueOf(color)
    } catch (exception: IllegalArgumentException) {
        throw InvalidGroupException(exception)
    }
}
