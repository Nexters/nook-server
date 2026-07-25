package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoPlaceResponse(val documents: List<Document> = emptyList()) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Document(
        val id: String?,
        @JsonProperty("place_name")
        val placeName: String?,
        @JsonProperty("category_name")
        val categoryName: String?,
        val phone: String?,
        @JsonProperty("address_name")
        val addressName: String?,
        @JsonProperty("road_address_name")
        val roadAddressName: String?,
        val x: String?,
        val y: String?,
        @JsonProperty("place_url")
        val placeUrl: String?,
    )
}
