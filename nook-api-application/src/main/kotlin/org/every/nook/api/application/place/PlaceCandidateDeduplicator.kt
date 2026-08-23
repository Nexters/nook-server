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

internal fun List<PlaceCandidateSelector.Candidate>.distinctLogicalCandidates():
    List<PlaceCandidateSelector.Candidate> =
    fold(mutableListOf()) { candidates, candidate ->
        val samePlaceIndex = candidates.indexOfFirst { existing ->
            existing.place.isSameLogicalPlace(candidate.place)
        }
        if (samePlaceIndex < 0) {
            candidates += candidate
        } else {
            val existing = candidates[samePlaceIndex]
            val representative = if (candidate.place.isMoreDetailedThan(existing.place)) {
                candidate.place
            } else {
                existing.place
            }
            candidates[samePlaceIndex] = PlaceCandidateSelector.Candidate(
                place = representative,
                matchedQueries = (existing.matchedQueries + candidate.matchedQueries).distinct(),
                matchedQueryRanks = (existing.matchedQueryRanks.keys + candidate.matchedQueryRanks.keys)
                    .associateWith { query ->
                        listOfNotNull(existing.matchedQueryRanks[query], candidate.matchedQueryRanks[query]).min()
                    },
                supportingProviders = existing.supportingProviders + candidate.supportingProviders,
            )
        }
        candidates
    }

private fun PlaceCandidate.isSameLogicalPlace(other: PlaceCandidate): Boolean {
    if (name.logicalNameKey() != other.name.logicalNameKey()) {
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

private fun String.logicalNameKey(): String {
    val tokens = lowercase().trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val withoutBranch = if (tokens.size > 1 && BRANCH_SUFFIXES.any(tokens.last()::endsWith)) {
        tokens.dropLast(1)
    } else {
        tokens
    }
    return withoutBranch.joinToString("").groundingKey()
}

private val BRANCH_SUFFIXES = listOf("플래그십스토어", "오프라인스토어", "본점", "점")
