package org.every.nook.api.application.post.model

import org.every.nook.api.domain.place.Place
import java.math.BigDecimal

data class PlaceView(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val bookmarked: Boolean,
) {
    companion object {
        fun from(place: Place, bookmarked: Boolean): PlaceView = PlaceView(
            id = requireNotNull(place.id),
            provider = place.providerReference.provider,
            externalPlaceId = place.providerReference.externalPlaceId,
            name = place.name,
            address = place.address,
            latitude = place.location.latitude,
            longitude = place.location.longitude,
            category = place.category,
            phoneNumber = place.phoneNumber,
            bookmarked = bookmarked,
        )
    }
}
