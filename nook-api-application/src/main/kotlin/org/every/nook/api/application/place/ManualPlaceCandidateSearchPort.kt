package org.every.nook.api.application.place

fun interface ManualPlaceCandidateSearchPort {
    fun findByName(name: String): List<PlaceCandidate>

    companion object {
        const val PROVIDER = "MANUAL"
    }
}
