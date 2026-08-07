package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceTag

fun interface PlaceTagExtractor {
    fun extract(request: Request): List<InferredPlaceTag>

    data class Request(
        val place: PlaceCandidate,
        val body: String?,
        val hashtags: List<String>,
        val imageUrls: List<String>,
    )
}

data class InferredPlaceTag(
    val tag: PlaceTag,
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
