package org.every.nook.api.application.place

interface PlaceSelectionTokenPort {
    fun issue(userId: Long, candidate: PlaceCandidate): String

    fun verify(userId: Long, token: String): PlaceCandidate?
}
