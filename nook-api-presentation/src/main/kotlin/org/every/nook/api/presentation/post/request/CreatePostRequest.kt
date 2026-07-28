package org.every.nook.api.presentation.post.request

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreatePostRequest(
    @field:Schema(description = "저장할 게시물 URL", example = "https://www.instagram.com/p/example", maxLength = 2048)
    @field:NotBlank
    @field:Size(max = MAX_URL_LENGTH)
    val url: String,
    @field:Schema(description = "사용자 메모", example = "다음 주말에 방문", maxLength = 2000, nullable = true)
    @field:Size(max = MAX_MEMO_LENGTH)
    val memo: String? = null,
    @field:Schema(description = "게시물을 함께 저장할 그룹 식별자 목록")
    @field:NotEmpty
    @field:Valid
    val groupIds: List<@Positive Long>,
) {
    @get:AssertTrue(message = "그룹 식별자는 양수여야 합니다.")
    @get:JsonIgnore
    @get:Schema(hidden = true)
    val areGroupIdsPositive: Boolean
        get() = groupIds.all { it > 0 }

    companion object {
        const val MAX_URL_LENGTH = 2048
        const val MAX_MEMO_LENGTH = 2000
    }
}
