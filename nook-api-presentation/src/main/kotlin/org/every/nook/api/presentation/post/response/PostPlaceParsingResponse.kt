package org.every.nook.api.presentation.post.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.post.FindPostPlaceParsingUseCase
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PlaceView
import java.math.BigDecimal

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PostPlaceParsingResponse(
    @field:Schema(description = "게시물 식별자")
    val postId: Long,
    @field:Schema(description = "게시물 장소 파싱 상태")
    val placeParsingStatus: PlaceParsingStatusView,
    @field:Schema(description = "장소 파싱 실패 사유. 실패 상태에서만 내려갑니다.", nullable = true)
    val failureReason: String? = null,
    @field:Schema(description = "파싱된 장소 목록. 완료 상태에서만 내려갑니다.", nullable = true)
    val places: List<PlaceResponse>? = null,
) {
    companion object {
        fun from(result: FindPostPlaceParsingUseCase.Result): PostPlaceParsingResponse = PostPlaceParsingResponse(
            postId = result.postId,
            placeParsingStatus = result.placeParsingStatus,
            failureReason = result.failureReason,
            places = result.places
                .takeIf { result.placeParsingStatus == PlaceParsingStatusView.COMPLETED }
                ?.map(PlaceResponse::from),
        )
    }
}

data class PlaceResponse(
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
    @field:Schema(description = "장소 대표 썸네일 URL", nullable = true)
    val thumbnailUrl: String?,
    @field:Schema(description = "장소 썸네일 파싱 상태")
    val thumbnailParsingStatus: PlaceThumbnailParsingStatusView,
    @field:Schema(description = "장소 대표 태그 목록(최대 4개)")
    val tags: List<String>,
    @field:Schema(description = "사용자의 장소 북마크 여부")
    val bookmarked: Boolean,
) {
    companion object {
        fun from(place: PlaceView): PlaceResponse = PlaceResponse(
            id = place.id,
            provider = place.provider,
            externalPlaceId = place.externalPlaceId,
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            category = place.category,
            phoneNumber = place.phoneNumber,
            thumbnailUrl = place.thumbnailUrl,
            thumbnailParsingStatus = place.thumbnailParsingStatus,
            tags = place.tags,
            bookmarked = place.bookmarked,
        )
    }
}
