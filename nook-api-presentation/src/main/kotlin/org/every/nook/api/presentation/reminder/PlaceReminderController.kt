package org.every.nook.api.presentation.reminder

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.every.nook.api.application.reminder.GetPlaceReminderUseCase
import org.every.nook.api.application.reminder.PlaceReminderView
import org.every.nook.api.application.reminder.UpdatePlaceReminderUseCase
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "PlaceReminder")
@RestController
@RequestMapping("/api/v1/me/place-connection-reminder")
class PlaceReminderController(
    private val getUseCase: GetPlaceReminderUseCase,
    private val updateUseCase: UpdatePlaceReminderUseCase,
) {
    @Operation(summary = "장소 연결 리마인드 설정 및 배너 대상 수 조회")
    @GetMapping
    fun get(@Parameter(hidden = true) userContext: UserContext): ApiResponse<PlaceReminderResponse> =
        ApiResponse.success(getUseCase(GetPlaceReminderUseCase.Query(userContext.userId)).toResponse())

    @Operation(summary = "장소 연결 리마인드 설정 변경")
    @PatchMapping
    fun update(
        @Parameter(hidden = true) userContext: UserContext,
        @Valid @RequestBody request: UpdatePlaceReminderRequest,
    ): ApiResponse<PlaceReminderResponse> = ApiResponse.success(
        updateUseCase(
            UpdatePlaceReminderUseCase.Command(userContext.userId, requireNotNull(request.enabled)),
        ).toResponse(),
    )

    private fun PlaceReminderView.toResponse() = PlaceReminderResponse(enabled, unlinkedPostCount, showBanner)
}

data class UpdatePlaceReminderRequest(
    @field:NotNull
    @field:Schema(description = "장소 연결 리마인드 사용 여부. 푸시 설정과 독립적")
    val enabled: Boolean?,
)

data class PlaceReminderResponse(
    @field:Schema(description = "장소 연결 리마인드 사용 여부. 최초 기본값 true")
    val enabled: Boolean,
    @field:Schema(description = "내 아카이브의 처리 종료된 장소 0개 게시물 수. 설정 OFF여도 실제 수 반환")
    val unlinkedPostCount: Long,
    @field:Schema(description = "enabled가 true이고 대상 게시물이 1개 이상이면 true. 배너 닫기/빈도 정책은 별도")
    val showBanner: Boolean,
)
