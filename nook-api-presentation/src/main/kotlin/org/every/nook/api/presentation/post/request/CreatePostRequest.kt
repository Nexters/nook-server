package org.every.nook.api.presentation.post.request

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = MAX_URL_LENGTH)
    val url: String,
    @field:Size(max = MAX_MEMO_LENGTH)
    val memo: String? = null,
    @field:Valid
    val groupIds: List<@Positive Long>? = null,
) {
    @get:AssertTrue(message = "그룹 식별자는 양수여야 합니다.")
    @get:JsonIgnore
    @get:Schema(hidden = true)
    val areGroupIdsPositive: Boolean
        get() = groupIds.orEmpty().all { it > 0 }

    companion object {
        const val MAX_URL_LENGTH = 2048
        const val MAX_MEMO_LENGTH = 2000
    }
}
