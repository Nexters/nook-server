package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.PlaceDetailQueryPort

class GetPlaceDetailUseCase(private val placeDetailQueryPort: PlaceDetailQueryPort) {
    operator fun invoke(query: Query): PlaceDetailView = placeDetailQueryPort.find(
        userId = query.userId,
        placeId = query.placeId,
        page = query.page,
        size = query.size,
    ) ?: throw PlaceNotFoundException()

    data class Query(val userId: Long, val placeId: Long, val page: Int, val size: Int)
}
