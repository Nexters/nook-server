package org.every.nook.api.application.place

import org.every.nook.api.domain.place.KoreanCityNameExtractor
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
    val distanceMeters: Int? = null,
    val city: String? = KoreanCityNameExtractor.extract(address),
    val googlePlaceId: String? = null,
    val sourceMediaSequence: Int? = null,
    val postMediaFallbackAllowed: Boolean = false,
)
