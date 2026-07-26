package org.every.nook.api.application.place

import java.math.BigDecimal

fun interface PlaceSearchProvider {
    fun search(request: Request): List<PlaceCandidate>

    data class Request(
        val query: String,
        val longitude: BigDecimal? = null,
        val latitude: BigDecimal? = null,
        val radius: Int? = null,
    )
}
