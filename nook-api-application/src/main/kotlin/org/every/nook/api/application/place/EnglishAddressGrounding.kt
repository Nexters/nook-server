package org.every.nook.api.application.place

internal fun PlaceClue.isSupportedBy(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String> = emptyList(),
): Boolean = if (!isCompatibleWithNormalizedAddress(candidate)) {
    false
} else if (validatedNormalizedAddress() != null) {
    hasNormalizedAddressSelectionGrounding(candidate, matchedQueries)
} else {
    isSupportedByOriginalAddress(candidate, matchedQueries)
}

internal fun PlaceClue.validatedNormalizedAddress(): String? {
    val original = addressHint?.trim() ?: return null
    val normalized = normalizedAddressHint?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val originalNumbers = ENGLISH_STREET_ADDRESS.findAll(original)
        .map { match -> match.groupValues.drop(1).first(String::isNotEmpty) }
        .toSet()
    return normalized.takeIf {
        PlaceAddressMatcher.addressKeys(original).isEmpty() &&
            originalNumbers.size == 1 &&
            PlaceAddressMatcher.preservesNormalizedDetails(original, normalized, originalNumbers) &&
            !hasCityConflict(original, normalized)
    }
}

internal fun PlaceClue.isCompatibleWithNormalizedAddress(candidate: PlaceCandidate): Boolean {
    val normalized = validatedNormalizedAddress() ?: return normalizedAddressHint.isNullOrBlank() ||
        addressHint?.let(ENGLISH_STREET_ADDRESS::containsMatchIn) != true
    return PlaceAddressMatcher.isCompatible(addressHint, candidate.address) &&
        PlaceAddressMatcher.isCompatible(normalized, candidate.address) &&
        !hasAdministrativeConflict(normalized, candidate.address)
}

internal fun PlaceClue.hasNormalizedAddressSelectionGrounding(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String>,
): Boolean {
    val normalized = validatedNormalizedAddress() ?: return false
    val original = addressHint ?: return false
    val candidateKeys = PlaceAddressMatcher.addressKeys(candidate.address)
    val matchesAddress = PlaceAddressMatcher.addressKeys(normalized).intersect(candidateKeys).isNotEmpty()
    val candidateName = candidate.name.addressIdentity()
    val hasNameIdentity = (listOf(name) + queries).any { it.supportsName(candidateName) }
    val searchedAddressOrName = matchedQueries.any {
        it.addressIdentity() == normalized.addressIdentity() || it.supportsName(candidateName)
    }
    val groundedInImage = evidence.isEmpty() || evidence.any {
        it.evidenceText.addressIdentity().contains(original.addressIdentity())
    }
    return matchesAddress && hasNameIdentity && searchedAddressOrName &&
        groundedInImage && isCompatibleWithNormalizedAddress(candidate)
}

private fun String.supportsName(candidateName: String): Boolean {
    val identity = addressIdentity()
    return candidateName.length >= MIN_NAME_LENGTH && identity.length >= MIN_NAME_LENGTH &&
        (identity.contains(candidateName) || candidateName.contains(identity))
}

private fun hasAdministrativeConflict(left: String, right: String): Boolean =
    listOf(CITY_PATTERN, DISTRICT_PATTERN).any { pattern ->
        val leftNames = pattern.findAll(left).map { it.groupValues[1] }.toSet()
        val rightNames = pattern.findAll(right).map { it.groupValues[1] }.toSet()
        leftNames.isNotEmpty() && rightNames.isNotEmpty() && leftNames.intersect(rightNames).isEmpty()
    }

private fun hasCityConflict(original: String, normalized: String): Boolean {
    val normalizedCities = CITY_PATTERN.findAll(normalized).map { it.groupValues[1] }.toSet()
    return CITY_NAMES.any { (english, korean) ->
        Regex("(?i)\\b$english\\b").containsMatchIn(original) && korean !in normalizedCities
    }
}

private fun String.addressIdentity(): String = lowercase().filter(Char::isLetterOrDigit)

private const val MIN_NAME_LENGTH = 2

private val ENGLISH_STREET_ADDRESS = Regex(
    "(?i)\\b(\\d+(?:-\\d+)?)\\s+[a-z][a-z0-9-]*-(?:ro|gil|dong|eup|myeon|ri)\\b|" +
        "\\b[a-z][a-z0-9-]*-(?:ro|gil|dong|eup|myeon|ri)\\s+(\\d+(?:-\\d+)?)\\b",
)
private val CITY_PATTERN = Regex(
    "(?:^|\\s)(서울|부산|대구|인천|광주|대전|울산|세종|[가-힣]+?시)(?:특별시|광역시|특별자치시)?(?=\\s|$)",
)
private val DISTRICT_PATTERN = Regex("(?:^|\\s)([가-힣]+(?:구|군))(?=\\s|$)")
private val CITY_NAMES = mapOf(
    "Seoul" to "서울",
    "Busan" to "부산",
    "Daegu" to "대구",
    "Incheon" to "인천",
    "Gwangju" to "광주",
    "Daejeon" to "대전",
    "Ulsan" to "울산",
    "Sejong" to "세종",
)
