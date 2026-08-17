package org.every.nook.api.presentation.group.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.group.GroupShareLinkView
import org.every.nook.api.presentation.response.toSeoulOffsetDateTime
import java.time.OffsetDateTime

data class GroupShareLinkResponse(
    @field:Schema(description = "공유 링크 토큰")
    val token: String,
    @field:Schema(description = "공유 링크 만료 시각. null이면 무기한", nullable = true)
    val expiresAt: OffsetDateTime?,
) {
    companion object {
        fun from(view: GroupShareLinkView): GroupShareLinkResponse = GroupShareLinkResponse(
            token = view.token,
            expiresAt = view.expiresAt?.toSeoulOffsetDateTime(),
        )
    }
}
