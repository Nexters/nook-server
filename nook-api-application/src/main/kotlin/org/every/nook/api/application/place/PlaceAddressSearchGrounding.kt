package org.every.nook.api.application.place

internal fun PlaceClue.hasGroundedExactAddressSearch(
    candidate: PlaceCandidate,
    matchedQueries: Collection<String>,
): Boolean {
    val hint = addressHint?.trim()?.takeIf(String::isNotEmpty) ?: return false
    val candidateAddressKeys = PlaceAddressMatcher.addressKeys(candidate.address)
    val hasExactAddress = PlaceAddressMatcher.addressKeys(hint).intersect(candidateAddressKeys).isNotEmpty()
    val normalizedHint = hint.normalizedAddressQuery()
    val matchedByFullAddress = matchedQueries.any { query -> query.normalizedAddressQuery() == normalizedHint }
    val normalizedVariants = PlaceAddressMatcher.searchVariants(hint)
        .drop(1)
        .map(String::normalizedAddressQuery)
        .toSet()
    val matchedByNormalizedAddress = matchedQueries.any { query ->
        query.normalizedAddressQuery() in normalizedVariants
    }
    val groundedInEvidence = evidence.isEmpty() || evidence.any { clueEvidence ->
        PlaceAddressMatcher.addressKeys(clueEvidence.evidenceText).intersect(candidateAddressKeys).isNotEmpty()
    }
    val hasSafeSearchIdentity = matchedByFullAddress ||
        (matchedByNormalizedAddress && hasNearHangulOcrIdentity(candidate))
    return hasExactAddress && hasSafeSearchIdentity && groundedInEvidence
}

private fun PlaceClue.hasNearHangulOcrIdentity(candidate: PlaceCandidate): Boolean {
    val candidateName = candidate.name.normalizedIdentity()
    return (sequenceOf(name) + queries.asSequence())
        .map(String::normalizedIdentity)
        .any { clueName -> HangulOcrMatcher.isNearMatch(clueName, candidateName) }
}

internal fun Collection<PlaceCandidateSelector.Candidate>.matchedQueriesFor(place: PlaceCandidate): List<String> =
    firstOrNull { candidate -> candidate.refersTo(place) }?.matchedQueries.orEmpty()

private fun PlaceCandidateSelector.Candidate.refersTo(place: PlaceCandidate): Boolean =
    this.place.provider == place.provider && this.place.externalPlaceId == place.externalPlaceId

private fun String.normalizedAddressQuery(): String = lowercase().filter(Char::isLetterOrDigit)

private fun String.normalizedIdentity(): String = lowercase().filter(Char::isLetterOrDigit)
