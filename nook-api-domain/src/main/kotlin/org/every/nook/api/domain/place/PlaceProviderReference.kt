package org.every.nook.api.domain.place

data class PlaceProviderReference(val provider: String, val externalPlaceId: String) {
    init {
        require(PROVIDER_PATTERN.matches(provider)) {
            "Place provider must contain only uppercase letters, numbers, and underscores"
        }
        require(provider.length <= MAX_PROVIDER_LENGTH) {
            "Place provider must not exceed $MAX_PROVIDER_LENGTH characters"
        }
        require(externalPlaceId.isNotBlank()) { "External place id must not be blank" }
        require(externalPlaceId.length <= MAX_EXTERNAL_PLACE_ID_LENGTH) {
            "External place id must not exceed $MAX_EXTERNAL_PLACE_ID_LENGTH characters"
        }
    }

    companion object {
        const val MAX_PROVIDER_LENGTH = 50
        const val MAX_EXTERNAL_PLACE_ID_LENGTH = 255
        private val PROVIDER_PATTERN = Regex("[A-Z][A-Z0-9_]*")
    }
}
