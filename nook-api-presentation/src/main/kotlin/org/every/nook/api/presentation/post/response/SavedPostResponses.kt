package org.every.nook.api.presentation.post.response

import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostPlace
import org.every.nook.api.application.post.model.SavedPostSummary
import java.math.BigDecimal
import java.time.Instant

data class SavedPostPageResponse(
    val items: List<SavedPostSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: SavedPostPage): SavedPostPageResponse = SavedPostPageResponse(
            items = result.items.map(SavedPostSummaryResponse::from),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }
}

data class SavedPostSummaryResponse(
    val postId: Long,
    val title: String?,
    val authorIdentifier: String?,
    val representativeMedia: SavedPostMediaResponse?,
    val memo: String?,
    val savedAt: Instant,
) {
    companion object {
        fun from(result: SavedPostSummary): SavedPostSummaryResponse = SavedPostSummaryResponse(
            postId = result.postId,
            title = result.title,
            authorIdentifier = result.authorIdentifier,
            representativeMedia = result.representativeMedia?.let(SavedPostMediaResponse::from),
            memo = result.memo,
            savedAt = result.savedAt,
        )
    }
}

data class SavedPostDetailResponse(
    val postId: Long,
    val title: String?,
    val body: String?,
    val authorIdentifier: String?,
    val canonicalUrl: String,
    val publishedAt: Instant?,
    val media: List<SavedPostMediaResponse>,
    val hashtags: List<String>,
    val memo: String?,
    val savedAt: Instant,
    val placeParsingStatus: PlaceParsingStatusView,
    val placeParsingFailureReason: String?,
    val places: List<SavedPostPlaceResponse>,
) {
    companion object {
        fun from(result: SavedPostDetail): SavedPostDetailResponse = SavedPostDetailResponse(
            postId = result.postId,
            title = result.title,
            body = result.body,
            authorIdentifier = result.authorIdentifier,
            canonicalUrl = result.canonicalUrl,
            publishedAt = result.publishedAt,
            media = result.media.map(SavedPostMediaResponse::from),
            hashtags = result.hashtags,
            memo = result.memo,
            savedAt = result.savedAt,
            placeParsingStatus = result.placeParsingStatus,
            placeParsingFailureReason = result.placeParsingFailureReason,
            places = result.places.map(SavedPostPlaceResponse::from),
        )
    }
}

data class SavedPostMediaResponse(val type: SavedPostMediaType, val url: String, val sequence: Int) {
    companion object {
        fun from(result: SavedPostMedia): SavedPostMediaResponse = SavedPostMediaResponse(
            type = result.type,
            url = result.url,
            sequence = result.sequence,
        )
    }
}

data class SavedPostPlaceResponse(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val bookmarked: Boolean,
    val sequence: Int,
) {
    companion object {
        fun from(result: SavedPostPlace): SavedPostPlaceResponse = SavedPostPlaceResponse(
            id = result.id,
            provider = result.provider,
            externalPlaceId = result.externalPlaceId,
            name = result.name,
            address = result.address,
            latitude = result.latitude,
            longitude = result.longitude,
            category = result.category,
            phoneNumber = result.phoneNumber,
            bookmarked = result.bookmarked,
            sequence = result.sequence,
        )
    }
}
