package org.every.nook.api.domain.place

data class PlaceParsingJob(
    val postId: Long,
    val status: PlaceParsingStatus,
    val failureReason: String? = null,
    val id: Long? = null,
) {
    init {
        require(id == null || id > 0) { "Place parsing job id must be positive" }
        require(postId > 0) { "Post id must be positive" }
        require(failureReason == null || failureReason.length <= MAX_FAILURE_REASON_LENGTH) {
            "Place parsing failure reason must not exceed $MAX_FAILURE_REASON_LENGTH characters"
        }
    }

    companion object {
        const val MAX_FAILURE_REASON_LENGTH = 500
    }
}
