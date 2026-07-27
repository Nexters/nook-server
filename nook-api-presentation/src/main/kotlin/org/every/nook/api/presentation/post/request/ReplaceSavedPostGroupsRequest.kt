package org.every.nook.api.presentation.post.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Positive

data class ReplaceSavedPostGroupsRequest(
    @field:Schema(
        description = "게시물을 연결할 전체 그룹 식별자 목록. 빈 목록이면 모든 그룹에서 제거합니다.",
        example = "[1, 2]",
    )
    @field:Valid
    val groupIds: List<@Positive Long>,
) {
    @get:AssertTrue(message = "그룹 식별자는 양수여야 합니다.")
    val areGroupIdsPositive: Boolean
        get() = groupIds.all { it > 0 }
}
