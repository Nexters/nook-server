package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.domain.group.Group
import org.every.nook.api.domain.group.GroupColor

class CreateGroupUseCase(private val groupPort: GroupPort) {
    operator fun invoke(command: Command): GroupView {
        val name = command.name.trim()
        val color = parseColor(command.color)
        val description = command.description?.trim()
        validateGroup(command.userId, name, color, description)
        return groupPort.create(command.userId, name, color, description?.takeIf(String::isNotEmpty))
    }

    data class Command(val userId: Long, val name: String, val color: String, val description: String? = null)

    private fun validateGroup(userId: Long, name: String, color: GroupColor, description: String?) {
        try {
            Group(userId = userId, name = name, color = color, description = description)
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
