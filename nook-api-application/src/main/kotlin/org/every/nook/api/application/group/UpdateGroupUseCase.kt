package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNameDuplicatedException
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.Group
import org.every.nook.api.domain.group.GroupColor

class UpdateGroupUseCase(private val groupPort: GroupPort) {
    operator fun invoke(command: Command): GroupView {
        val name = command.name.trim()
        val color = parseColor(command.color)
        validateGroup(command.userId, command.groupId, name, color)
        return when (val result = groupPort.update(command.userId, command.groupId, name, color)) {
            is GroupPort.UpdateResult.Updated -> result.group
            GroupPort.UpdateResult.NotFound -> throw GroupNotFoundException()
            GroupPort.UpdateResult.DuplicateName -> throw GroupNameDuplicatedException()
        }
    }

    data class Command(val userId: Long, val groupId: Long, val name: String, val color: String)

    private fun validateGroup(userId: Long, groupId: Long, name: String, color: GroupColor) {
        try {
            Group(userId = userId, name = name, color = color, id = groupId)
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
