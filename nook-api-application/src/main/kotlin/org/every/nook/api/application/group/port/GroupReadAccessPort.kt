package org.every.nook.api.application.group.port

fun interface GroupReadAccessPort {
    fun resolveOwnerId(memberId: Long, groupId: Long): Long?
}
