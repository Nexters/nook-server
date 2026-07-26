package org.every.nook.api.presentation.post.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = MAX_URL_LENGTH)
    val url: String,
    @field:Size(max = MAX_MEMO_LENGTH)
    val memo: String? = null,
) {
    companion object {
        const val MAX_URL_LENGTH = 2048
        const val MAX_MEMO_LENGTH = 2000
    }
}
