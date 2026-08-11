package org.every.nook.api.application.place

data class ClaimedPlaceParsingJob(
    val postId: Long,
    val attempt: Int,
    val body: String?,
    val hashtags: List<String>,
    val sourceLocationTag: String?,
    val imageUrls: List<String> = emptyList(),
    val textClues: List<PlaceClue>? = null,
)
