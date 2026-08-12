package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverPlaceResponse(val items: List<Item> = emptyList()) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val title: String?,
        val link: String?,
        val category: String?,
        val description: String?,
        val telephone: String?,
        val address: String?,
        val roadAddress: String?,
        val mapx: String?,
        val mapy: String?,
    )
}
