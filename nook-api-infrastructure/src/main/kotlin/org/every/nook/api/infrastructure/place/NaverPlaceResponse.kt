package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverPlaceResponse(val addresses: List<Address> = emptyList()) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Address(
        val roadAddress: String?,
        val jibunAddress: String?,
        val englishAddress: String?,
        val address: String?,
        val x: String?,
        val y: String?,
    )
}
