package org.every.nook.api.application.place

internal fun PlaceClue.isSafeAddressMismatchRecovery(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String>,
): Boolean = isSafeExactNameSearchResult(candidate, matchedQueries) ||
    isSafeNameAtExplicitAddressSearchResult(candidate, matchedQueries)

internal fun PlaceClue.isSafeExactNameSearchResult(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String>,
): Boolean {
    val clueName = name.recoveryKey()
    val locationHints = listOfNotNull(region, addressHint).filter(String::isNotBlank)
    val hasAdministrativeAreaConflict = locationHints.any { hint ->
        PlaceLocationConflictMatcher.hasAdministrativeAreaConflict(hint, candidate.address)
    }
    val hasLocationDetailConflict = addressHint?.let { hint ->
        PlaceLocationConflictMatcher.hasLocationDetailConflict(hint, candidate.address)
    } == true
    return candidate.name.recoveryKey() == clueName &&
        matchedQueries.any { query -> query.recoveryKey() == clueName } &&
        !hasAdministrativeAreaConflict &&
        !hasLocationDetailConflict
}

internal fun PlaceClue.isSafeNameAtExplicitAddressSearchResult(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String>,
): Boolean {
    val hint = addressHint?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val clueName = name.recoveryKey()
    val candidateName = candidate.name.recoveryKey()
    val hintAddressKeys = PlaceAddressMatcher.addressKeys(hint)
    val wasFoundByExplicitAddress = hintAddressKeys.isNotEmpty() && matchedQueries.any { query ->
        PlaceAddressMatcher.addressKeys(query).intersect(hintAddressKeys).isNotEmpty()
    }
    val locationHints = listOfNotNull(region, addressHint).filter(String::isNotBlank)
    val hasAdministrativeAreaConflict = locationHints.any { locationHint ->
        PlaceLocationConflictMatcher.hasAdministrativeAreaConflict(locationHint, candidate.address)
    }
    val hasLocationDetailConflict = PlaceLocationConflictMatcher.hasLocationDetailConflict(hint, candidate.address)
    return clueName.length >= MIN_BRANCH_NAME_RECOVERY_LENGTH &&
        candidateName.startsWith(clueName) &&
        wasFoundByExplicitAddress &&
        !hasAdministrativeAreaConflict &&
        !hasLocationDetailConflict
}

private object PlaceLocationConflictMatcher {
    fun hasAdministrativeAreaConflict(left: String, right: String): Boolean = hasTypedValueConflict(
        left = administrativeAreas(left),
        right = administrativeAreas(right),
    )

    fun hasLocationDetailConflict(left: String, right: String): Boolean = hasTypedValueConflict(
        left = locationDetails(left),
        right = locationDetails(right),
    )

    private fun hasTypedValueConflict(left: Set<TypedValue>, right: Set<TypedValue>): Boolean {
        val rightByType = right.groupBy(TypedValue::type)
        return left.groupBy(TypedValue::type).any { (type, values) ->
            val rightValues = rightByType[type]?.mapTo(mutableSetOf(), TypedValue::value)
                ?: return@any false
            values.none { it.value in rightValues }
        }
    }

    private fun administrativeAreas(value: String): Set<TypedValue> =
        ADMINISTRATIVE_AREA_PATTERN.findAll(value).map { match ->
            TypedValue(type = match.groupValues[2], value = match.value)
        }.toSet()

    private fun locationDetails(value: String): Set<TypedValue> = buildSet {
        BASEMENT_PATTERN.findAll(value).forEach { match ->
            add(TypedValue(FLOOR_TYPE, "-${match.groupValues.drop(1).first(String::isNotEmpty)}"))
        }
        val aboveGround = BASEMENT_PATTERN.replace(value, " ")
        FLOOR_PATTERN.findAll(aboveGround).forEach { match ->
            add(TypedValue(FLOOR_TYPE, match.groupValues[1]))
        }
        ROOM_PATTERN.findAll(value).forEach { match ->
            add(TypedValue(ROOM_TYPE, match.groupValues[1]))
        }
    }

    private data class TypedValue(val type: String, val value: String)

    private val ADMINISTRATIVE_AREA_PATTERN = Regex("([가-힣0-9]+)(구|군|읍|면|동|리)")
    private val BASEMENT_PATTERN = Regex("(?i)(?:\\bB\\s*-?\\s*(\\d+)|지(?:하)?\\s*(\\d+)\\s*층)")
    private val FLOOR_PATTERN = Regex("(?i)(?<![-\\d])([1-9]\\d?)\\s*(?:층|F)(?=$|[^가-힣A-Za-z0-9])")
    private val ROOM_PATTERN = Regex("(?<![-\\d])([1-9]\\d{0,3})\\s*호(?=$|[^가-힣A-Za-z0-9])")
    private const val FLOOR_TYPE = "floor"
    private const val ROOM_TYPE = "room"
}

private fun String.recoveryKey(): String = lowercase().filter(Char::isLetterOrDigit)

private const val MIN_BRANCH_NAME_RECOVERY_LENGTH = 3
