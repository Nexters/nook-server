package org.every.nook.api.application.place

internal fun Collection<PlaceCandidateSelector.Candidate>.compatibleWith(
    clue: PlaceClue,
): List<PlaceCandidateSelector.Candidate> {
    val addressHint = clue.addressHint?.trim()?.takeIf(String::isNotEmpty) ?: return toList()
    val addressCompatible = filter { candidate ->
        PlaceAddressMatcher.isCompatible(addressHint, candidate.place.address)
    }
    if (addressCompatible.isNotEmpty()) return addressCompatible

    val exactNameCandidates = filter { candidate ->
        clue.isSafeAddressMismatchRecovery(candidate.place, candidate.matchedQueries)
    }.toList().distinctLogicalCandidates()
    return exactNameCandidates.takeIf { it.size == 1 }.orEmpty()
}

internal fun PlaceClue.isSupportedBy(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String> = emptyList(),
): Boolean {
    val explicitAddressHint = addressHint?.trim()?.takeIf(String::isNotEmpty)
    if (explicitAddressHint != null &&
        !PlaceAddressMatcher.isCompatible(explicitAddressHint, candidate.address) &&
        !isSafeAddressMismatchRecovery(candidate, matchedQueries)
    ) {
        return false
    }
    val hasGroundedAddressSearch = hasGroundedExactAddressSearch(candidate, matchedQueries)

    val hasExactAddressEvidence = explicitAddressHint?.let { hint ->
        PlaceAddressMatcher.addressKeys(hint).intersect(PlaceAddressMatcher.addressKeys(candidate.address)).isNotEmpty()
    } == true
    val hasCompatibleIdentity = hasCompatibleName(candidate) ||
        hasNameEvidence(candidate, allowShortName = hasExactAddressEvidence)
    val hasAddressBackedOcrIdentity = explicitAddressHint != null && hasPlausibleOcrIdentity(candidate)
    val lacksCandidateIdentity = !hasCompatibleIdentity && !hasAddressBackedOcrIdentity
    if (
        explicitAddressHint != null &&
        !hasGroundedAddressSearch &&
        lacksCandidateIdentity
    ) {
        return false
    }
    return hasGroundedAddressSearch ||
        evidence.isEmpty() ||
        hasCompatibleIdentity ||
        hasAddressBackedOcrIdentity ||
        hasCompatibleEvidence(candidate)
}

internal fun PlaceClue.hasPlausibleOcrIdentity(candidate: PlaceCandidate): Boolean {
    val candidateName = candidate.name.groundingKey()
    return (sequenceOf(name) + queries.asSequence())
        .map(String::groundingKey)
        .any { it.isNearOcrMatch(candidateName) }
}

private fun String.isNearOcrMatch(other: String): Boolean {
    if (length < MIN_NEAR_OCR_NAME_LENGTH || other.length < MIN_NEAR_OCR_NAME_LENGTH) return false
    if (kotlin.math.abs(length - other.length) > MAX_OCR_NAME_EDIT_DISTANCE) return false
    val distances = IntArray(other.length + 1) { it }
    forEachIndexed { leftIndex, left ->
        var previous = distances[0]
        distances[0] = leftIndex + 1
        other.forEachIndexed { rightIndex, right ->
            val replaced = previous + if (left == right) 0 else 1
            previous = distances[rightIndex + 1]
            distances[rightIndex + 1] = minOf(distances[rightIndex + 1] + 1, distances[rightIndex] + 1, replaced)
        }
    }
    return distances.last() <= MAX_OCR_NAME_EDIT_DISTANCE
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

private fun PlaceClue.hasNameEvidence(candidate: PlaceCandidate, allowShortName: Boolean): Boolean {
    val candidateName = candidate.name.groundingKey()
    val minimumLength = if (allowShortName) 1 else MIN_GROUNDING_KEY_LENGTH
    return candidateName.length >= minimumLength && evidence.any { clueEvidence ->
        clueEvidence.evidenceText.groundingKey().contains(candidateName)
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
private const val MIN_NEAR_OCR_NAME_LENGTH = 3
private const val MAX_OCR_NAME_EDIT_DISTANCE = 3
private const val MIN_ADDRESS_GROUNDING_KEY_LENGTH = 6
