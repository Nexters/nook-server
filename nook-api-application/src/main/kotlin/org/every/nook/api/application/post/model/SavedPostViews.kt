package org.every.nook.api.application.post.model

import java.math.BigDecimal
import java.time.Instant

data class SavedPostPage(
    val items: List<SavedPostSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

data class SavedPostSummary(
    val postId: Long,
    val title: String?,
    val authorIdentifier: String?,
    val representativeMedia: SavedPostMedia?,
    val memo: String?,
    val savedAt: Instant,
    val processingStatus: PostProcessingStatusView = PostProcessingStatusView.COMPLETED,
    val processingStage: PostProcessingStageView? = null,
)

data class SavedPostDetail(
    val postId: Long,
    val title: String?,
    val body: String?,
    val authorIdentifier: String?,
    val canonicalUrl: String,
    val publishedAt: Instant?,
    val media: List<SavedPostMedia>,
    val hashtags: List<String>,
    val memo: String?,
    val savedAt: Instant,
    val groups: List<SavedPostGroup>,
    val placeParsingStatus: PlaceParsingStatusView,
    val placeParsingFailureReason: String?,
    val places: List<SavedPostPlace>,
    val processingStatus: PostProcessingStatusView = PostProcessingStatusView.COMPLETED,
    val processingStage: PostProcessingStageView? = null,
)

data class SavedPostGroup(val id: Long, val name: String, val color: String)

data class SavedPostMedia(val type: SavedPostMediaType, val url: String, val sequence: Int)

enum class SavedPostMediaType {
    IMAGE,
    VIDEO,
}

data class SavedPostPlace(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val thumbnailUrl: String?,
    val tags: List<String> = emptyList(),
    val bookmarked: Boolean,
    val sequence: Int,
)
