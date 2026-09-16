package org.every.nook.api.presentation.group.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.group.GroupAccessType
import org.every.nook.api.application.group.GroupOwnerView
import org.every.nook.api.application.group.GroupView
import java.time.Instant

data class GroupResponse(
    @field:Schema(description = "그룹 식별자", example = "17")
    val id: Long,
    @field:Schema(description = "그룹명", example = "초록뷰 카페")
    val name: String,
    @field:Schema(description = "그룹 색상 코드", example = "YELLOW")
    val color: String,
    @field:Schema(description = "그룹에 포함된 저장 게시물 수", example = "3")
    val postCount: Long,
    @field:Schema(
        description = "내 아카이브에 마지막으로 게시물을 저장한 시각. 저장 이력이 없거나 공유 아카이브면 null",
        format = "date-time",
        nullable = true,
    )
    val lastSavedAt: Instant?,
    @field:Schema(
        description = "이미지가 있는 최신 저장 게시물의 대표 이미지 URL 목록. 최신순으로 최대 3개입니다.",
        example = """["https://cdn.example.com/posts/1.jpg", "https://cdn.example.com/posts/2.jpg"]""",
    )
    val thumbnailUrls: List<String>,
    @field:Schema(description = "그룹 접근 유형")
    val accessType: GroupAccessType,
    @field:Schema(description = "공유 그룹 소유자. 내 그룹이면 null", nullable = true)
    val owner: GroupOwnerResponse?,
    @field:Schema(description = "공유 그룹 접근 토큰. 내 그룹이면 null", nullable = true)
    val shareToken: String?,
) {
    companion object {
        fun from(view: GroupView): GroupResponse = GroupResponse(
            id = view.id,
            name = view.name,
            color = view.color,
            postCount = view.postCount,
            lastSavedAt = view.lastSavedAt,
            thumbnailUrls = view.thumbnailUrls,
            accessType = view.accessType,
            owner = view.owner?.let(GroupOwnerResponse::from),
            shareToken = view.shareToken,
        )
    }
}

data class GroupOwnerResponse(
    @field:Schema(description = "그룹 소유자 닉네임")
    val nickname: String,
    @field:Schema(description = "그룹 소유자 프로필 이미지 URL", nullable = true)
    val profileImageUrl: String?,
) {
    companion object {
        fun from(view: GroupOwnerView): GroupOwnerResponse = GroupOwnerResponse(view.nickname, view.profileImageUrl)
    }
}
