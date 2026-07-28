package org.every.nook.api.presentation.post.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PostProcessingStageView
import org.every.nook.api.application.post.model.PostProcessingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostPlace
import org.every.nook.api.application.post.model.SavedPostSummary
import java.math.BigDecimal
import java.time.Instant

data class SavedPostPageResponse(
    @field:Schema(description = "저장 게시물 목록")
    val items: List<SavedPostSummaryResponse>,
    @field:Schema(description = "현재 페이지 번호")
    val page: Int,
    @field:Schema(description = "페이지당 게시물 수")
    val size: Int,
    @field:Schema(description = "전체 게시물 수")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수")
    val totalPages: Int,
    @field:Schema(description = "다음 페이지 존재 여부")
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
    @field:Schema(description = "저장 게시물 식별자")
    val postId: Long,
    @field:Schema(description = "게시물 제목", nullable = true)
    val title: String?,
    @field:Schema(description = "게시물 작성자 식별자", nullable = true)
    val authorIdentifier: String?,
    @field:Schema(description = "대표 미디어", nullable = true)
    val representativeMedia: SavedPostMediaResponse?,
    @field:Schema(description = "사용자 메모", nullable = true)
    val memo: String?,
    @field:Schema(description = "게시물 저장 시각")
    val savedAt: Instant,
    @field:Schema(description = "게시물 전체 처리 상태")
    val processingStatus: PostProcessingStatusView,
    @field:Schema(description = "현재 처리 단계. 모든 처리가 완료되면 null입니다.", nullable = true)
    val processingStage: PostProcessingStageView?,
) {
    companion object {
        fun from(result: SavedPostSummary): SavedPostSummaryResponse = SavedPostSummaryResponse(
            postId = result.postId,
            title = result.title,
            authorIdentifier = result.authorIdentifier,
            representativeMedia = result.representativeMedia?.let(SavedPostMediaResponse::from),
            memo = result.memo,
            savedAt = result.savedAt,
            processingStatus = result.processingStatus,
            processingStage = result.processingStage,
        )
    }
}

data class SavedPostDetailResponse(
    @field:Schema(description = "저장 게시물 식별자")
    val postId: Long,
    @field:Schema(description = "게시물 제목", nullable = true)
    val title: String?,
    @field:Schema(description = "게시물 본문", nullable = true)
    val body: String?,
    @field:Schema(description = "게시물 작성자 식별자", nullable = true)
    val authorIdentifier: String?,
    @field:Schema(description = "게시물 원본 URL")
    val canonicalUrl: String,
    @field:Schema(description = "게시물 발행 시각", nullable = true)
    val publishedAt: Instant?,
    @field:Schema(description = "게시물 미디어 목록")
    val media: List<SavedPostMediaResponse>,
    @field:Schema(description = "게시물 해시태그 목록")
    val hashtags: List<String>,
    @field:Schema(description = "사용자 메모", nullable = true)
    val memo: String?,
    @field:Schema(description = "게시물 저장 시각")
    val savedAt: Instant,
    @field:Schema(description = "게시물 장소 파싱 상태")
    val placeParsingStatus: PlaceParsingStatusView,
    @field:Schema(description = "장소 파싱 실패 사유", nullable = true)
    val placeParsingFailureReason: String?,
    @field:Schema(description = "게시물에서 파싱된 장소 목록")
    val places: List<SavedPostPlaceResponse>,
    @field:Schema(description = "게시물 전체 처리 상태")
    val processingStatus: PostProcessingStatusView,
    @field:Schema(description = "현재 처리 단계. 모든 처리가 완료되면 null입니다.", nullable = true)
    val processingStage: PostProcessingStageView?,
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
            processingStatus = result.processingStatus,
            processingStage = result.processingStage,
        )
    }
}

data class SavedPostMediaResponse(
    @field:Schema(description = "미디어 유형")
    val type: SavedPostMediaType,
    @field:Schema(description = "미디어 URL")
    val url: String,
    @field:Schema(description = "게시물 내 미디어 순서")
    val sequence: Int,
) {
    companion object {
        fun from(result: SavedPostMedia): SavedPostMediaResponse = SavedPostMediaResponse(
            type = result.type,
            url = result.url,
            sequence = result.sequence,
        )
    }
}

data class SavedPostPlaceResponse(
    @field:Schema(description = "장소 식별자")
    val id: Long,
    @field:Schema(description = "장소 provider")
    val provider: String,
    @field:Schema(description = "provider의 장소 식별자")
    val externalPlaceId: String,
    @field:Schema(description = "장소명")
    val name: String,
    @field:Schema(description = "장소 주소")
    val address: String,
    @field:Schema(description = "장소 위도")
    val latitude: BigDecimal,
    @field:Schema(description = "장소 경도")
    val longitude: BigDecimal,
    @field:Schema(description = "장소 카테고리", nullable = true)
    val category: String?,
    @field:Schema(description = "장소 전화번호", nullable = true)
    val phoneNumber: String?,
    @field:Schema(description = "사용자의 장소 북마크 여부")
    val bookmarked: Boolean,
    @field:Schema(description = "게시물 내 장소 순서")
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
