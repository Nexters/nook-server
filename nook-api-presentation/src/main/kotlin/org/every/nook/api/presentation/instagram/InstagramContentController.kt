package org.every.nook.api.presentation.instagram

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.instagram.ExtractedInstagramContent
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Instagram Content")
@RestController
@RequestMapping("/api/v1/instagram/contents")
class InstagramContentController(private val extractInstagramContentUseCase: ExtractInstagramContentUseCase) {
    @Operation(summary = "지도 등록용 Instagram 게시물·릴스 정보 추출")
    @PostMapping("/extract")
    fun extract(
        @Valid @RequestBody request: ExtractInstagramContentRequest,
    ): ApiResponse<ExtractInstagramContentResponse> {
        val extracted = extractInstagramContentUseCase(request.url)
        return ApiResponse.success(ExtractInstagramContentResponse.from(extracted))
    }
}

data class ExtractInstagramContentRequest(
    @field:NotBlank
    val url: String,
)

data class ExtractInstagramContentResponse(
    val canonicalUrl: String,
    val shortcode: String,
    val contentType: ExtractedInstagramContent.ContentType,
    val description: String?,
    val hashtags: List<String>,
    val thumbnailUrl: String?,
    val media: List<MediaResponse>,
    val locationNames: List<String>,
    val locationDetails: LocationDetailsResponse?,
) {
    data class MediaResponse(val type: ExtractedInstagramContent.MediaType, val url: String, val sequence: Int)

    data class LocationDetailsResponse(
        val id: String?,
        val name: String?,
        val latitude: Double?,
        val longitude: Double?,
        val imageUrl: String?,
    )

    companion object {
        fun from(content: ExtractedInstagramContent): ExtractInstagramContentResponse = ExtractInstagramContentResponse(
            canonicalUrl = content.canonicalUrl,
            shortcode = content.shortcode,
            contentType = content.contentType,
            description = content.description,
            hashtags = content.hashtags,
            thumbnailUrl = content.thumbnailUrl,
            media = content.media.map {
                MediaResponse(type = it.type, url = it.url, sequence = it.sequence)
            },
            locationNames = content.locationNames,
            locationDetails = content.locationDetails?.let {
                LocationDetailsResponse(
                    id = it.id,
                    name = it.name,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    imageUrl = it.imageUrl,
                )
            },
        )
    }
}
