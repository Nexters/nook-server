package org.every.nook.api.application.group.port

fun interface GroupOwnershipPort {
    fun ownsAll(userId: Long, groupIds: Set<Long>): Boolean
}
