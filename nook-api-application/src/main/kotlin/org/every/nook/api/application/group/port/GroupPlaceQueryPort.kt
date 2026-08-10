package org.every.nook.api.application.group.port

import org.every.nook.api.application.group.GroupPlacePage

fun interface GroupPlaceQueryPort {
    fun findPlaces(userId: Long, groupId: Long, page: Int, size: Int): GroupPlacePage?
}
