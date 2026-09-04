package org.every.nook.api.infrastructure.place

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoAddressResponse(val meta: Meta = Meta(), val documents: List<Document> = emptyList()) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Meta(
        @JsonProperty("total_count")
        val totalCount: Int = 0,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Document(
        @JsonProperty("address_name")
        val addressName: String? = null,
        val x: String? = null,
        val y: String? = null,
        @JsonProperty("road_address")
        val roadAddress: RoadAddress? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RoadAddress(
        @JsonProperty("address_name")
        val addressName: String? = null,
    )
}
