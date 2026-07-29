package org.every.nook.api.application.place.port

import org.every.nook.api.application.place.MapPlaceView
import org.every.nook.api.application.place.RecentPlaceCursor
import org.every.nook.api.application.place.RecentPlaceView
import org.every.nook.api.domain.place.GeoBounds

interface PlaceMapQueryPort {
    fun findInBounds(userId: Long, bounds: GeoBounds): List<MapPlaceView>

    fun findRecent(userId: Long, cursor: RecentPlaceCursor?, limit: Int): List<RecentPlaceView>
}
