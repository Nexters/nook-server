package org.every.nook.api.application.place

internal fun Collection<PlaceCandidateSelector.Candidate>.compatibleWith(
    clue: PlaceClue,
): List<PlaceCandidateSelector.Candidate> {
    val addressHint = clue.addressHint?.trim()?.takeIf(String::isNotEmpty) ?: return toList()
    return filter { candidate -> PlaceAddressMatcher.isCompatible(addressHint, candidate.place.address) }
}

internal fun PlaceClue.isSupportedBy(candidate: PlaceCandidate): Boolean {
    val explicitAddressHint = addressHint?.trim()?.takeIf(String::isNotEmpty)
    if (explicitAddressHint != null && !PlaceAddressMatcher.isCompatible(explicitAddressHint, candidate.address)) {
        return false
    }

    val hasCompatibleName = hasCompatibleName(candidate)
    if (explicitAddressHint != null && !hasCompatibleName) {
        return false
    }
    return evidence.isEmpty() || hasCompatibleName || hasCompatibleEvidence(candidate)
}

internal fun Collection<PlaceCandidateSelector.Candidate>.descriptions(limit: Int): List<String> =
    take(limit).map { candidate -> "${candidate.place.name}|${candidate.place.address}" }

private fun PlaceClue.hasCompatibleName(candidate: PlaceCandidate): Boolean {
    val candidateName = candidate.name.groundingKey()
    val candidateAddress = candidate.address.groundingKey()
    return (sequenceOf(name) + queries.asSequence())
        .map(String::groundingKey)
        .any { queryName ->
            candidateName == queryName ||
                candidateName.isCompatibleName(queryName) ||
                candidateName.isFuzzyNameMatch(queryName) ||
                (queryName.length >= MIN_NAME_COMPATIBILITY_KEY_LENGTH && candidateAddress.contains(queryName))
        }
}

private fun PlaceClue.hasCompatibleEvidence(candidate: PlaceCandidate): Boolean {
    val candidateName = candidate.name.groundingKey()
    val candidateAddress = candidate.address.groundingKey()
    val candidateAddressKeys = PlaceAddressMatcher.addressKeys(candidate.address)
    return evidence.asSequence().map(PlaceClueEvidence::evidenceText).any { evidenceText ->
        val normalizedEvidence = evidenceText.groundingKey()
        val containsName = candidateName.length >= MIN_GROUNDING_KEY_LENGTH &&
            normalizedEvidence.contains(candidateName)
        val containsAddress = candidateAddress.length >= MIN_ADDRESS_GROUNDING_KEY_LENGTH &&
            normalizedEvidence.contains(candidateAddress)
        containsName || containsAddress ||
            candidateAddressKeys.any { it in PlaceAddressMatcher.addressKeys(evidenceText) }
    }
}

private fun String.isCompatibleName(other: String): Boolean = length >= MIN_NAME_COMPATIBILITY_KEY_LENGTH &&
    other.length >= MIN_NAME_COMPATIBILITY_KEY_LENGTH &&
    (contains(other) || other.contains(this))

private fun String.isFuzzyNameMatch(other: String): Boolean = length >= MIN_FUZZY_NAME_LENGTH &&
    length == other.length && zip(other).count { (left, right) -> left != right } <= MAX_NAME_CHARACTER_DIFFERENCE

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

private const val MIN_GROUNDING_KEY_LENGTH = 2
private const val MIN_NAME_COMPATIBILITY_KEY_LENGTH = 3
private const val MIN_FUZZY_NAME_LENGTH = 4
private const val MAX_NAME_CHARACTER_DIFFERENCE = 1
private const val MIN_ADDRESS_GROUNDING_KEY_LENGTH = 6
