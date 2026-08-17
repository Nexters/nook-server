package org.every.nook.api.application.place.port

import org.every.nook.api.application.place.PlaceDetailView

fun interface SharedPlaceDetailQueryPort {
    fun findInGroup(userId: Long, groupId: Long, placeId: Long, page: Int, size: Int): PlaceDetailView?
}
