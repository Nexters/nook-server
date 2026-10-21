package org.every.nook.api.presentation.group.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.group.GroupSubscriberPage
import org.every.nook.api.application.group.GroupSubscriberView

data class GroupSubscriberResponse(
    @field:Schema(description = "추가한 사람의 현재 닉네임")
    val nickname: String,
    @field:Schema(description = "프로필 이미지 URL. 없으면 null", nullable = true)
    val profileImageUrl: String?,
) {
    companion object {
        fun from(view: GroupSubscriberView) = GroupSubscriberResponse(view.nickname, view.profileImageUrl)
    }
}

data class GroupSubscriberPageResponse(
    @field:Schema(description = "추가한 사람 목록. 최근 추가순이며 읽기 전용입니다.")
    val items: List<GroupSubscriberResponse>,
    @field:Schema(description = "현재 페이지 번호. 0부터 시작합니다.")
    val page: Int,
    @field:Schema(description = "페이지당 사용자 수")
    val size: Int,
    @field:Schema(description = "중복을 제외한 전체 사용자 수")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수")
    val totalPages: Int,
    @field:Schema(description = "다음 페이지 존재 여부")
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: GroupSubscriberPage) = GroupSubscriberPageResponse(
            items = result.items.map(GroupSubscriberResponse::from),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }
}
