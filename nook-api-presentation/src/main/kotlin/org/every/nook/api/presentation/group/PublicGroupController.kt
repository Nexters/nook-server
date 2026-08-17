package org.every.nook.api.presentation.group

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.every.nook.api.application.group.GetSharedGroupUseCase
import org.every.nook.api.application.group.GetSharedPlaceDetailUseCase
import org.every.nook.api.application.group.GetSharedPostDetailUseCase
import org.every.nook.api.application.group.ListSharedGroupPlacesUseCase
import org.every.nook.api.application.group.ListSharedGroupPostsUseCase
import org.every.nook.api.presentation.group.response.GroupPlacePageResponse
import org.every.nook.api.presentation.group.response.GroupPostPageResponse
import org.every.nook.api.presentation.group.response.GroupResponse
import org.every.nook.api.presentation.place.response.PlaceDetailResponse
import org.every.nook.api.presentation.post.response.SavedPostDetailResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MAX_PUBLIC_PAGE_SIZE = 100L

@Tag(name = "Public Group")
@Validated
@RestController
@RequestMapping("/api/v1/public/groups/{token}")
class PublicGroupController(
    private val getGroupUseCase: GetSharedGroupUseCase,
    private val listPostsUseCase: ListSharedGroupPostsUseCase,
    private val listPlacesUseCase: ListSharedGroupPlacesUseCase,
    private val getPostDetailUseCase: GetSharedPostDetailUseCase,
    private val getPlaceDetailUseCase: GetSharedPlaceDetailUseCase,
) {
    @Operation(summary = "공유 그룹 정보 조회")
    @GetMapping
    fun get(@Parameter(description = "공유 링크 토큰") @PathVariable token: String): ApiResponse<GroupResponse> =
        ApiResponse.success(GroupResponse.from(getGroupUseCase(token).group))

    @Operation(summary = "공유 그룹 게시물 목록 조회")
    @GetMapping("/posts")
    fun posts(
        @Parameter(description = "공유 링크 토큰") @PathVariable token: String,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 게시물 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_PUBLIC_PAGE_SIZE)
        size: Int,
    ): ApiResponse<GroupPostPageResponse> =
        ApiResponse.success(GroupPostPageResponse.from(listPostsUseCase(token, page, size)))

    @Operation(summary = "공유 그룹 장소 목록 조회")
    @GetMapping("/places")
    fun places(
        @Parameter(description = "공유 링크 토큰") @PathVariable token: String,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 장소 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_PUBLIC_PAGE_SIZE)
        size: Int,
    ): ApiResponse<GroupPlacePageResponse> =
        ApiResponse.success(GroupPlacePageResponse.from(listPlacesUseCase(token, page, size)))

    @Operation(summary = "공유 그룹 게시물 상세 조회")
    @GetMapping("/posts/{postId}")
    fun postDetail(
        @Parameter(description = "공유 링크 토큰") @PathVariable token: String,
        @Parameter(description = "저장 게시물 식별자") @PathVariable @Positive postId: Long,
        @Parameter(hidden = true) authentication: Authentication?,
    ): ApiResponse<SavedPostDetailResponse> = ApiResponse.success(
        SavedPostDetailResponse.from(
            getPostDetailUseCase(token, postId, authentication?.name?.toLongOrNull()),
        ),
    )

    @Operation(summary = "공유 그룹 장소 상세 조회")
    @GetMapping("/places/{placeId}")
    fun placeDetail(
        @Parameter(description = "공유 링크 토큰") @PathVariable token: String,
        @Parameter(description = "장소 식별자") @PathVariable @Positive placeId: Long,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 게시물 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_PUBLIC_PAGE_SIZE)
        size: Int,
    ): ApiResponse<PlaceDetailResponse> = ApiResponse.success(
        PlaceDetailResponse.from(getPlaceDetailUseCase(token, placeId, page, size)),
    )
}
