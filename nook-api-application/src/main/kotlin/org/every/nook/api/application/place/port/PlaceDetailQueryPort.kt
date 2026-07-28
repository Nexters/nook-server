package org.every.nook.api.application.place.port

import org.every.nook.api.application.place.PlaceDetailView

fun interface PlaceDetailQueryPort {
    fun find(userId: Long, placeId: Long, page: Int, size: Int): PlaceDetailView?
}
