package org.every.nook.api.presentation.post.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class ReplaceSavedPostsGroupsRequest(
    @field:Schema(description = "그룹 소속을 함께 변경할 저장 게시물 식별자 목록", example = "[11, 12]")
    @field:Size(min = 1, max = 100)
    @field:Valid
    val postIds: List<@Positive Long>,
    @field:Schema(description = "게시물을 연결할 전체 그룹 식별자 목록", example = "[1, 2]")
    @field:Size(min = 1, max = 100)
    @field:Valid
    val groupIds: List<@Positive Long>,
)
