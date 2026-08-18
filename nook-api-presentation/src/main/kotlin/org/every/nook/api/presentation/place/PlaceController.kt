package org.every.nook.api.presentation.place

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.every.nook.api.application.place.GetMapPlacesUseCase
import org.every.nook.api.application.place.GetPlaceDetailUseCase
import org.every.nook.api.application.place.GetRecentPlacesUseCase
import org.every.nook.api.application.place.SearchPlacesUseCase
import org.every.nook.api.application.place.SearchSavedPlacesUseCase
import org.every.nook.api.application.place.UpdatePlaceBookmarkUseCase
import org.every.nook.api.application.place.UpdatePlaceMemoUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.place.request.UpdatePlaceBookmarkRequest
import org.every.nook.api.presentation.place.request.UpdatePlaceMemoRequest
import org.every.nook.api.presentation.place.response.MapPlaceResponse
import org.every.nook.api.presentation.place.response.PlaceDetailResponse
import org.every.nook.api.presentation.place.response.PlaceSearchSliceResponse
import org.every.nook.api.presentation.place.response.RecentPlaceSliceResponse
import org.every.nook.api.presentation.place.response.SavedPlaceSearchPageResponse
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

private const val MAX_PLACE_POST_PAGE_SIZE = 100L
private const val MAX_RECENT_PLACE_PAGE_SIZE = 100L
private const val MAX_PLACE_SEARCH_PAGE = 44L
private const val MAX_PLACE_SEARCH_PAGE_SIZE = 15L
private const val MAX_SAVED_PLACE_SEARCH_PAGE_SIZE = 100L

@Tag(name = "Place")
@Validated
@RestController
@RequestMapping("/api/v1/places")
class PlaceController(
    private val updatePlaceBookmarkUseCase: UpdatePlaceBookmarkUseCase,
    private val updatePlaceMemoUseCase: UpdatePlaceMemoUseCase,
    private val getPlaceDetailUseCase: GetPlaceDetailUseCase,
    private val getMapPlacesUseCase: GetMapPlacesUseCase,
    private val getRecentPlacesUseCase: GetRecentPlacesUseCase,
    private val searchPlacesUseCase: SearchPlacesUseCase,
    private val searchSavedPlacesUseCase: SearchSavedPlacesUseCase,
) {
    @Operation(summary = "지도 영역의 북마크 장소 조회")
    @GetMapping("/map")
    fun getMapPlaces(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "지도 북쪽 경계 위도")
        @RequestParam
        @DecimalMin("-90")
        @DecimalMax("90")
        northLatitude: BigDecimal,
        @Parameter(description = "지도 서쪽 경계 경도")
        @RequestParam
        @DecimalMin("-180")
        @DecimalMax("180")
        westLongitude: BigDecimal,
        @Parameter(description = "지도 남쪽 경계 위도")
        @RequestParam
        @DecimalMin("-90")
        @DecimalMax("90")
        southLatitude: BigDecimal,
        @Parameter(description = "지도 동쪽 경계 경도")
        @RequestParam
        @DecimalMin("-180")
        @DecimalMax("180")
        eastLongitude: BigDecimal,
    ): ApiResponse<List<MapPlaceResponse>> {
        val places = getMapPlacesUseCase(
            GetMapPlacesUseCase.Query(
                userId = userContext.userId,
                northLatitude = northLatitude,
                westLongitude = westLongitude,
                southLatitude = southLatitude,
                eastLongitude = eastLongitude,
            ),
        )
        return ApiResponse.success(places.map(MapPlaceResponse::from))
    }

    @Operation(summary = "최근 저장 공간 조회")
    @GetMapping("/recent")
    fun getRecentPlaces(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "다음 목록 조회 cursor")
        @RequestParam(required = false)
        cursor: String?,
        @Parameter(description = "조회할 공간 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_RECENT_PLACE_PAGE_SIZE)
        size: Int,
    ): ApiResponse<RecentPlaceSliceResponse> {
        val places = getRecentPlacesUseCase(
            GetRecentPlacesUseCase.Query(
                userId = userContext.userId,
                cursor = RecentPlaceCursorCodec.decode(cursor),
                size = size,
            ),
        )
        return ApiResponse.success(RecentPlaceSliceResponse.from(places))
    }

    @Operation(summary = "내 저장 장소 검색")
    @GetMapping("/saved/search")
    fun searchSavedPlaces(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "장소명, 주소 또는 카테고리 검색 문자열")
        @RequestParam
        @NotBlank
        @Size(max = SearchSavedPlacesUseCase.MAX_QUERY_LENGTH)
        query: String,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 장소 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_SAVED_PLACE_SEARCH_PAGE_SIZE)
        size: Int,
    ): ApiResponse<SavedPlaceSearchPageResponse> {
        val result = searchSavedPlacesUseCase(
            SearchSavedPlacesUseCase.Query(
                userId = userContext.userId,
                keyword = query,
                page = page,
                size = size,
            ),
        )
        return ApiResponse.success(SavedPlaceSearchPageResponse.from(result))
    }

    @Operation(summary = "직접 연결할 장소 검색")
    @GetMapping("/search")
    fun searchPlaces(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "검색할 장소명 또는 장소명과 지역")
        @RequestParam
        query: String,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        @Max(MAX_PLACE_SEARCH_PAGE)
        page: Int,
        @Parameter(description = "페이지당 장소 수")
        @RequestParam(defaultValue = "15")
        @Min(1)
        @Max(MAX_PLACE_SEARCH_PAGE_SIZE)
        size: Int,
        @Parameter(description = "거리 계산 기준 위도")
        @RequestParam(required = false)
        @DecimalMin("-90")
        @DecimalMax("90")
        latitude: BigDecimal?,
        @Parameter(description = "거리 계산 기준 경도")
        @RequestParam(required = false)
        @DecimalMin("-180")
        @DecimalMax("180")
        longitude: BigDecimal?,
    ): ApiResponse<PlaceSearchSliceResponse> {
        val result = searchPlacesUseCase(
            SearchPlacesUseCase.Query(
                userId = userContext.userId,
                keyword = query,
                page = page,
                size = size,
                longitude = longitude,
                latitude = latitude,
            ),
        )
        return ApiResponse.success(PlaceSearchSliceResponse.from(result))
    }

    @Operation(summary = "장소 상세 및 연관 게시물 조회")
    @GetMapping("/{placeId}")
    fun getDetail(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "조회할 장소 식별자")
        @PathVariable
        @Positive
        placeId: Long,
        @Parameter(description = "조회할 페이지 번호. 0부터 시작합니다.")
        @RequestParam(defaultValue = "0")
        @Min(0)
        page: Int,
        @Parameter(description = "페이지당 게시물 수")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(MAX_PLACE_POST_PAGE_SIZE)
        size: Int,
    ): ApiResponse<PlaceDetailResponse> {
        val detail = getPlaceDetailUseCase(
            GetPlaceDetailUseCase.Query(
                userId = userContext.userId,
                placeId = placeId,
                page = page,
                size = size,
            ),
        )
        return ApiResponse.success(PlaceDetailResponse.from(detail))
    }

    @Operation(summary = "장소 북마크 변경")
    @PatchMapping("/{placeId}/bookmark")
    fun updateBookmark(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "북마크를 변경할 장소 식별자")
        @PathVariable
        @Positive
        placeId: Long,
        @Valid @RequestBody request: UpdatePlaceBookmarkRequest,
    ): ApiResponse<Unit> {
        updatePlaceBookmarkUseCase(
            UpdatePlaceBookmarkUseCase.Command(
                userId = userContext.userId,
                placeId = placeId,
                bookmarked = request.bookmarked,
            ),
        )
        return ApiResponse.success(Unit)
    }

    @Operation(summary = "내 장소 메모 변경")
    @PatchMapping("/{placeId}/memo")
    fun updateMemo(
        @Parameter(hidden = true) userContext: UserContext,
        @Parameter(description = "메모를 변경할 장소 식별자")
        @PathVariable
        @Positive
        placeId: Long,
        @Valid @RequestBody request: UpdatePlaceMemoRequest,
    ): ApiResponse<Unit> {
        updatePlaceMemoUseCase(
            UpdatePlaceMemoUseCase.Command(
                userId = userContext.userId,
                placeId = placeId,
                memo = request.memo,
            ),
        )
        return ApiResponse.success(Unit)
    }
}
