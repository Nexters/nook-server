package org.every.nook.api.application.place

data class PlaceClue(
    val name: String,
    val region: String?,
    val queries: List<String>,
    val evidence: List<PlaceClueEvidence> = emptyList(),
    val addressHint: String? = null,
)

data class PlaceClueEvidence(val imageIndex: Int, val evidenceText: String)
