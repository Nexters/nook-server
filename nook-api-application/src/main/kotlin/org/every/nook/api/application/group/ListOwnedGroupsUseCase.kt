package org.every.nook.api.application.group

import org.every.nook.api.application.group.port.GroupPort

class ListOwnedGroupsUseCase(private val groupPort: GroupPort) {
    operator fun invoke(userId: Long): List<GroupView> = groupPort.findOwned(userId)
}
