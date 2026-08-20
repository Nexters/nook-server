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
    val groundedInEvidence = evidence.isEmpty() || evidence.any { clueEvidence ->
        PlaceAddressMatcher.addressKeys(clueEvidence.evidenceText).intersect(candidateAddressKeys).isNotEmpty()
    }
    return hasExactAddress && matchedByFullAddress && groundedInEvidence
}

internal fun Collection<PlaceCandidateSelector.Candidate>.matchedQueriesFor(place: PlaceCandidate): List<String> =
    firstOrNull { candidate -> candidate.refersTo(place) }?.matchedQueries.orEmpty()

private fun PlaceCandidateSelector.Candidate.refersTo(place: PlaceCandidate): Boolean =
    this.place.provider == place.provider && this.place.externalPlaceId == place.externalPlaceId

private fun String.normalizedAddressQuery(): String = lowercase().filter(Char::isLetterOrDigit)
