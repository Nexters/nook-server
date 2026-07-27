package org.every.nook.api.presentation.group.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.group.GroupView

data class GroupResponse(
    @field:Schema(description = "그룹 식별자", example = "17")
    val id: Long,
    @field:Schema(description = "그룹명", example = "초록뷰 카페")
    val name: String,
    @field:Schema(description = "그룹 색상 코드", example = "YELLOW")
    val color: String,
    @field:Schema(description = "그룹에 포함된 저장 게시물 수", example = "3")
    val postCount: Long,
) {
    companion object {
        fun from(view: GroupView): GroupResponse = GroupResponse(
            id = view.id,
            name = view.name,
            color = view.color,
            postCount = view.postCount,
        )
    }
}
