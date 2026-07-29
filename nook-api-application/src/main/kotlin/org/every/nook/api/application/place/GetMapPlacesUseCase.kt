package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.every.nook.api.domain.place.GeoBounds
import java.math.BigDecimal

class GetMapPlacesUseCase(private val queryPort: PlaceMapQueryPort) {
    operator fun invoke(query: Query): List<MapPlaceView> = queryPort.findInBounds(
        userId = query.userId,
        bounds = GeoBounds(
            northLatitude = query.northLatitude,
            westLongitude = query.westLongitude,
            southLatitude = query.southLatitude,
            eastLongitude = query.eastLongitude,
        ),
    )

    data class Query(
        val userId: Long,
        val northLatitude: BigDecimal,
        val westLongitude: BigDecimal,
        val southLatitude: BigDecimal,
        val eastLongitude: BigDecimal,
    )
}
