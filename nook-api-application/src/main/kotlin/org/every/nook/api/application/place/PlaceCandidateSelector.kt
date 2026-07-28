package org.every.nook.api.application.place

fun interface PlaceCandidateSelector {
    fun select(request: Request): PlaceCandidate?

    data class Request(val clue: PlaceClue, val candidates: List<Candidate>)

    data class Candidate(val place: PlaceCandidate, val matchedQueries: List<String>)
}
