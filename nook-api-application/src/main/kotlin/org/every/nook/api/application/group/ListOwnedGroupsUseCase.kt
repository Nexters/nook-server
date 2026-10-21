package org.every.nook.api.application.group

import org.every.nook.api.application.group.port.GroupPort
import org.every.nook.api.application.group.port.GroupSubscribersPort

class ListOwnedGroupsUseCase(private val groupPort: GroupPort, private val subscribersPort: GroupSubscribersPort) {
    operator fun invoke(userId: Long): List<GroupView> = groupPort.findOwned(userId).withSubscribers(subscribersPort)
}
