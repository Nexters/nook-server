package org.every.nook.api.application.place

internal fun List<PlaceCandidate>.distinctLogicalPlaces(): List<PlaceCandidate> =
    fold(mutableListOf()) { places, candidate ->
        val samePlaceIndex = places.indexOfFirst { existing -> existing.isSameLogicalPlace(candidate) }
        when {
            samePlaceIndex < 0 -> places += candidate
            candidate.isMoreDetailedThan(places[samePlaceIndex]) -> places[samePlaceIndex] = candidate
        }
        places
    }

private fun PlaceCandidate.isSameLogicalPlace(other: PlaceCandidate): Boolean {
    if (name.groundingKey() != other.name.groundingKey()) {
        return false
    }
    val addressKeys = PlaceAddressMatcher.addressKeys(address)
    val otherAddressKeys = PlaceAddressMatcher.addressKeys(other.address)
    return addressKeys.isNotEmpty() &&
        otherAddressKeys.isNotEmpty() &&
        addressKeys.intersect(otherAddressKeys).isNotEmpty()
}

private fun PlaceCandidate.isMoreDetailedThan(other: PlaceCandidate): Boolean =
    PlaceAddressMatcher.hasLocationDetail(address) && !PlaceAddressMatcher.hasLocationDetail(other.address)

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)
