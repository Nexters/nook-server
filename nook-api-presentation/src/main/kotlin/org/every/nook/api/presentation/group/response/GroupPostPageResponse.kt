package org.every.nook.api.presentation.group.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.group.GroupPostPage
import org.every.nook.api.application.group.GroupPostSummary
import org.every.nook.api.presentation.post.response.SavedPostMediaResponse
import java.time.Instant

data class GroupPostPageResponse(
    @field:Schema(description = "그룹 소유자의 닉네임")
    val ownerNickname: String,
    @field:Schema(description = "그룹에 저장된 게시물 목록")
    val items: List<GroupPostSummaryResponse>,
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
        fun from(result: GroupPostPage): GroupPostPageResponse = GroupPostPageResponse(
            ownerNickname = result.ownerNickname,
            items = result.items.map(GroupPostSummaryResponse::from),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }
}

data class GroupPostSummaryResponse(
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
    @field:Schema(description = "게시물에 연결된 장소 개수")
    val placeCount: Long,
    @field:Schema(description = "게시물 저장 시각")
    val savedAt: Instant,
) {
    companion object {
        fun from(result: GroupPostSummary): GroupPostSummaryResponse = GroupPostSummaryResponse(
            postId = result.post.postId,
            title = result.post.title,
            authorIdentifier = result.post.authorIdentifier,
            representativeMedia = result.post.representativeMedia?.let(SavedPostMediaResponse::from),
            memo = result.post.memo,
            placeCount = result.placeCount,
            savedAt = result.post.savedAt,
        )
    }
}
