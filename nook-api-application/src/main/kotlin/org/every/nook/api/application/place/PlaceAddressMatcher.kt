package org.every.nook.api.application.place

internal object PlaceAddressMatcher {
    fun isCompatible(addressHint: String?, candidateAddress: String): Boolean {
        val hint = addressHint?.trim()?.takeIf(String::isNotEmpty) ?: return true
        val hintAddressKeys = addressKeys(hint)
        val candidateAddressKeys = addressKeys(candidateAddress)
        if (
            hintAddressKeys.isNotEmpty() &&
            candidateAddressKeys.isNotEmpty() &&
            hintAddressKeys.intersect(candidateAddressKeys).isEmpty()
        ) {
            return false
        }

        val hintDetails = locationDetails(hint).groupBy(LocationDetail::type)
        val candidateDetails = locationDetails(candidateAddress).groupBy(LocationDetail::type)
        return hintDetails.all { (type, details) ->
            val candidateValues = candidateDetails[type]?.mapTo(mutableSetOf(), LocationDetail::value)
                ?: return@all true
            details.any { it.value in candidateValues }
        }
    }

    fun addressKeys(value: String): Set<String> = BASE_ADDRESS_PATTERN.findAll(value).map { match ->
        match.groupValues.drop(1).joinToString(separator = "").groundingKey()
    }.toSet()

    private fun locationDetails(value: String): Set<LocationDetail> {
        val basementDetails = BASEMENT_PATTERN.findAll(value).map { match ->
            LocationDetail(LocationDetailType.FLOOR, "-${match.firstCapturedValue()}")
        }.toSet()
        val aboveGroundSource = BASEMENT_PATTERN.replace(value, " ")
        val floorDetails = FLOOR_PATTERN.findAll(aboveGroundSource).map { match ->
            LocationDetail(LocationDetailType.FLOOR, match.groupValues[1])
        }
        val roomDetails = ROOM_PATTERN.findAll(value).map { match ->
            LocationDetail(LocationDetailType.ROOM, match.groupValues[1])
        }
        return basementDetails + floorDetails + roomDetails
    }

    private fun MatchResult.firstCapturedValue(): String = groupValues.drop(1).first(String::isNotEmpty)

    private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

    private enum class LocationDetailType {
        FLOOR,
        ROOM,
    }

    private data class LocationDetail(val type: LocationDetailType, val value: String)

    private val BASE_ADDRESS_PATTERN = Regex(
        "([가-힣A-Za-z0-9]+(?:대로|로|길|동|읍|면|리))\\s*(\\d+(?:-\\d+)?)",
    )
    private val BASEMENT_PATTERN = Regex(
        "(?i)(?:\\bB\\s*-?\\s*(\\d+)|지(?:하)?\\s*(\\d+)\\s*층)",
    )
    private val FLOOR_PATTERN = Regex(
        "(?i)(?<![-\\d])([1-9]\\d?)\\s*(?:층|F)(?=$|[^가-힣A-Za-z0-9])",
    )
    private val ROOM_PATTERN = Regex(
        "(?<![-\\d])([1-9]\\d{0,3})\\s*호(?=$|[^가-힣A-Za-z0-9])",
    )
}
