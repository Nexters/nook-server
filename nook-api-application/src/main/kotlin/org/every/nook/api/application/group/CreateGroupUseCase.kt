package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.Group
import org.every.nook.api.domain.group.GroupColor

class CreateGroupUseCase(private val groupPort: GroupPort) {
    operator fun invoke(command: Command): GroupView {
        val name = command.name.trim()
        val color = parseColor(command.color)
        validateGroup(command.userId, name, color)
        return groupPort.create(command.userId, name, color)
    }

    data class Command(val userId: Long, val name: String, val color: String)

    private fun validateGroup(userId: Long, name: String, color: GroupColor) {
        try {
            Group(userId = userId, name = name, color = color)
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
