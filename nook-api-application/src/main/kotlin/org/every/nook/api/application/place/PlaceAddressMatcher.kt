package org.every.nook.api.application.place

internal object PlaceAddressMatcher {
    fun isCompatible(addressHint: String?, candidateAddress: String): Boolean {
        val hint = addressHint?.trim()?.takeIf(String::isNotEmpty) ?: return true
        val hintAddressKeys = addressKeys(hint)
        val candidateAddressKeys = addressKeys(candidateAddress)
        val bothHaveAddressKeys = hintAddressKeys.isNotEmpty() && candidateAddressKeys.isNotEmpty()
        val hasCompatibleAddressKey = hintAddressKeys.intersect(candidateAddressKeys).isNotEmpty() ||
            hasNearOcrRoadAddress(hint, candidateAddress)
        if (bothHaveAddressKeys && !hasCompatibleAddressKey) {
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

    fun addressKeys(value: String): Set<String> = BASE_ADDRESS_PATTERN.findAll(value).flatMap { match ->
        val roadName = match.groupValues[1].groundingKey()
        val buildingNumber = match.groupValues[2].groundingKey()
        val buildingNumbers = buildSet {
            add(buildingNumber)
            if (buildingNumber.length >= MIN_COMPACT_BUILDING_FLOOR_LENGTH && match.isFollowedByFloorSuffix(value)) {
                add(buildingNumber.dropLast(1))
            }
        }
        buildingNumbers.asSequence().flatMap { number ->
            sequenceOf(
                roadName + number,
                roadName.removeSuffix(ROAD_SUFFIX) + number,
            )
        }
    }.toSet()

    fun hasLocationDetail(value: String?): Boolean = value != null && locationDetails(value).isNotEmpty()

    fun searchVariants(value: String): List<String> {
        val fullAddress = value.trim()
        val baseAddress = BASE_ADDRESS_PATTERN.find(fullAddress)
            ?.let { match -> fullAddress.substring(0, match.range.last + 1).trimEnd(',', ' ') }
            ?: fullAddress
        val withoutProvince = PROVINCE_PREFIX_PATTERN.replaceFirst(baseAddress, "").trim()
        return listOf(fullAddress, baseAddress, withoutProvince)
            .filter(String::isNotEmpty)
            .distinct()
    }

    private fun hasNearOcrRoadAddress(left: String, right: String): Boolean {
        val leftAddresses = roadAddresses(left)
        val rightAddresses = roadAddresses(right)
        val leftDistricts = DISTRICT_PATTERN.findAll(left).map { it.value }.toSet()
        val rightDistricts = DISTRICT_PATTERN.findAll(right).map { it.value }.toSet()
        val districtsConflict = leftDistricts.isNotEmpty() &&
            rightDistricts.isNotEmpty() &&
            leftDistricts.intersect(rightDistricts).isEmpty()
        if (districtsConflict) {
            return false
        }
        return leftAddresses.any { leftAddress ->
            rightAddresses.any { rightAddress ->
                leftAddress.buildingNumber == rightAddress.buildingNumber &&
                    leftAddress.roadName.editDistanceAtMostOne(rightAddress.roadName)
            }
        }
    }

    private fun roadAddresses(value: String): List<RoadAddress> = BASE_ADDRESS_PATTERN.findAll(value).map { match ->
        RoadAddress(match.groupValues[1].groundingKey(), match.groupValues[2].groundingKey())
    }.toList()

    private fun String.editDistanceAtMostOne(other: String): Boolean {
        if (kotlin.math.abs(length - other.length) > 1) return false
        if (length == other.length) return zip(other).count { (left, right) -> left != right } <= 1
        val (shorter, longer) = if (length < other.length) this to other else other to this
        var shortIndex = 0
        var longIndex = 0
        var differences = 0
        while (shortIndex < shorter.length && longIndex < longer.length && differences <= 1) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex += 1
            } else {
                differences += 1
            }
            longIndex += 1
        }
        return differences <= 1
    }

    private fun locationDetails(value: String): Set<LocationDetail> {
        val basementDetails = BASEMENT_PATTERN.findAll(value).map { match ->
            LocationDetail(LocationDetailType.FLOOR, "-${match.firstCapturedValue()}")
        }.toSet()
        val aboveGroundSource = BASEMENT_PATTERN.replace(value, " ")
        val floorDetails = FLOOR_PATTERN.findAll(aboveGroundSource).map { match ->
            LocationDetail(LocationDetailType.FLOOR, match.groupValues[1])
        }
        val compactFloorDetails = COMPACT_BUILDING_FLOOR_PATTERN.findAll(aboveGroundSource).map { match ->
            LocationDetail(LocationDetailType.FLOOR, match.groupValues[1])
        }
        val roomDetails = ROOM_PATTERN.findAll(value).map { match ->
            LocationDetail(LocationDetailType.ROOM, match.groupValues[1])
        }
        return basementDetails + floorDetails + compactFloorDetails + roomDetails
    }

    private fun MatchResult.isFollowedByFloorSuffix(source: String): Boolean =
        source.substring(range.last + 1).trimStart().startsWith(FLOOR_SUFFIX)

    private fun MatchResult.firstCapturedValue(): String = groupValues.drop(1).first(String::isNotEmpty)

    private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

    private enum class LocationDetailType {
        FLOOR,
        ROOM,
    }

    private data class LocationDetail(val type: LocationDetailType, val value: String)

    private data class RoadAddress(val roadName: String, val buildingNumber: String)

    private val BASE_ADDRESS_PATTERN = Regex(
        "([가-힣A-Za-z]+(?:대로|로|길)(?:\\d+[가-힣]?(?:길)?)?|" +
            "[가-힣A-Za-z0-9]+(?:동|읍|면|리))\\s+(\\d+(?:-\\d+)?)",
    )
    private val BASEMENT_PATTERN = Regex(
        "(?i)(?:\\bB\\s*-?\\s*(\\d+)|지(?:하)?\\s*(\\d+)\\s*층)",
    )
    private val FLOOR_PATTERN = Regex(
        "(?i)(?<![-\\d])([1-9]\\d?)\\s*(?:층|F)(?=$|[^가-힣A-Za-z0-9])",
    )
    private val COMPACT_BUILDING_FLOOR_PATTERN = Regex(
        "(?<!\\d)\\d{2,}([1-9])\\s*층(?=$|[^가-힣A-Za-z0-9])",
    )
    private val ROOM_PATTERN = Regex(
        "(?<![-\\d])([1-9]\\d{0,3})\\s*호(?=$|[^가-힣A-Za-z0-9])",
    )
    private val DISTRICT_PATTERN = Regex("[가-힣]+(?:구|군|시)")
    private val PROVINCE_PREFIX_PATTERN = Regex(
        "^[가-힣]+(?:특별시|광역시|특별자치시|특별자치도|도)?\\s+(?=[가-힣]+(?:구|군|시)\\s)",
    )

    private const val ROAD_SUFFIX = "길"
    private const val FLOOR_SUFFIX = "층"
    private const val MIN_COMPACT_BUILDING_FLOOR_LENGTH = 3
}
