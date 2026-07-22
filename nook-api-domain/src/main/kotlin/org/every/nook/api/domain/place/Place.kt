package org.every.nook.api.domain.place

data class Place(
    val providerReference: PlaceProviderReference,
    val name: String,
    val address: String,
    val location: GeoPoint,
    val category: String? = null,
    val phoneNumber: String? = null,
    val id: Long? = null,
) {
    init {
        require(id == null || id > 0) { "Place id must be positive" }
        require(name.isNotBlank()) { "Place name must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Place name must not exceed $MAX_NAME_LENGTH characters" }
        require(address.isNotBlank()) { "Place address must not be blank" }
        require(address.length <= MAX_ADDRESS_LENGTH) {
            "Place address must not exceed $MAX_ADDRESS_LENGTH characters"
        }
        require(category == null || category.length <= MAX_CATEGORY_LENGTH) {
            "Place category must not exceed $MAX_CATEGORY_LENGTH characters"
        }
        require(phoneNumber == null || phoneNumber.length <= MAX_PHONE_NUMBER_LENGTH) {
            "Place phone number must not exceed $MAX_PHONE_NUMBER_LENGTH characters"
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 255
        const val MAX_ADDRESS_LENGTH = 500
        const val MAX_CATEGORY_LENGTH = 100
        const val MAX_PHONE_NUMBER_LENGTH = 30
    }
}
