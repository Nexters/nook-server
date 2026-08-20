package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceTagDefinition

fun interface PlaceTagExtractor {
    fun extract(request: Request): List<Result>

    data class Request(val places: List<PlaceInput>) {
        init {
            require(places.isNotEmpty()) { "Place tag inputs must not be empty" }
        }
    }

    data class PlaceInput(
        val placeIndex: Int,
        val place: PlaceCandidate,
        val body: String?,
        val hashtags: List<String>,
        val candidateTags: List<PlaceTagDefinition>,
    )

    data class Result(val placeIndex: Int, val tags: List<InferredPlaceTag>)
}

data class InferredPlaceTag(
    val tag: String,
    val confidence: Double,
    val evidenceSource: PlaceTagEvidenceSource,
    val evidenceText: String,
) {
    init {
        require(confidence in MIN_CONFIDENCE..MAX_CONFIDENCE) { "Tag confidence must be between 0 and 1" }
        require(evidenceText.isNotBlank()) { "Tag evidence must not be blank" }
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.0
        const val MAX_CONFIDENCE = 1.0
    }
}

enum class PlaceTagEvidenceSource {
    BODY,
    HASHTAG,
    IMAGE_TEXT,
    IMAGE_VISUAL,
}
