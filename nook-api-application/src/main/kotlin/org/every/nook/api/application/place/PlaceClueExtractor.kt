package org.every.nook.api.application.place

import org.every.nook.api.application.content.SourceProfileHint

fun interface PlaceClueExtractor {
    fun extract(request: Request): List<PlaceClue>

    data class Request(
        val body: String?,
        val hashtags: List<String>,
        val sourceLocationTag: String?,
        val imageTranscripts: List<ImageTranscript> = emptyList(),
        val sourceProfileHints: List<SourceProfileHint> = emptyList(),
    )
}
