package org.every.nook.api.presentation.save.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SaveInstagramPostRequest(
    @field:NotBlank
    @field:Size(max = MAX_INSTAGRAM_URL_LENGTH)
    val instagramUrl: String,
) {
    companion object {
        const val MAX_INSTAGRAM_URL_LENGTH = 2048
    }
}
