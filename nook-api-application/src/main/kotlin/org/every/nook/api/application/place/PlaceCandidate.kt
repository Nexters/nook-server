package org.every.nook.api.application.place

import java.math.BigDecimal

data class PlaceCandidate(
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val providerUrl: String?,
)
