package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPort

class DeleteGroupUseCase(private val groupPort: GroupPort) {
    operator fun invoke(command: Command) {
        if (!groupPort.delete(command.userId, command.groupId)) {
            throw GroupNotFoundException()
        }
    }

    data class Command(val userId: Long, val groupId: Long)
}
