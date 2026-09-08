package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.every.nook.api.application.analytics.AnalyticsActivityQuery
import org.every.nook.api.application.analytics.GetAnalyticsActivityMembersUseCase
import org.every.nook.api.application.analytics.GetAnalyticsMemberEventsUseCase
import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

@Tag(name = "AdminUserAnalytics")
@RestController
@RequestMapping("/api/admin/v1/user-analytics/activity")
class AdminAnalyticsActivityController(
    private val getMembers: GetAnalyticsActivityMembersUseCase,
    private val getEvents: GetAnalyticsMemberEventsUseCase,
) {
    @Operation(summary = "선택 기간·이벤트의 활동 회원 조회")
    @GetMapping("/members")
    fun members(
        @Parameter(description = "KST 시작일") @RequestParam from: LocalDate,
        @Parameter(description = "KST 종료일 포함. 최대 90일") @RequestParam to: LocalDate,
        @Parameter(description = "이벤트 이름. 생략하면 가입 포함 전체")
        @RequestParam(required = false) eventName: String? = null,
        @Parameter(description = "0부터 시작하는 페이지") @RequestParam(defaultValue = "0") page: Int = 0,
        @Parameter(description = "페이지 크기 1~100") @RequestParam(defaultValue = "20") size: Int = 20,
    ): ApiResponse<AnalyticsActivityPageResponse<AnalyticsActivityMemberResponse>> {
        val result = getMembers(AnalyticsActivityQuery(from, to, parseEventName(eventName), page, size))
        return ApiResponse.success(
            AnalyticsActivityPageResponse(
                result.items.map {
                    AnalyticsActivityMemberResponse(
                        it.memberId,
                        it.nickname,
                        it.events,
                        it.lastEventAt,
                    )
                },
                result.total,
                result.page,
                result.size,
            ),
        )
    }

    @Operation(summary = "선택 회원의 기간·이벤트별 활동 타임라인 조회")
    @GetMapping("/events")
    fun events(
        @Parameter(description = "KST 시작일") @RequestParam from: LocalDate,
        @Parameter(description = "KST 종료일 포함. 최대 90일") @RequestParam to: LocalDate,
        @Parameter(description = "회원 ID") @RequestParam memberId: Long,
        @Parameter(description = "이벤트 이름. 생략하면 가입 포함 전체")
        @RequestParam(required = false) eventName: String? = null,
        @Parameter(description = "0부터 시작하는 페이지") @RequestParam(defaultValue = "0") page: Int = 0,
        @Parameter(description = "페이지 크기 1~100") @RequestParam(defaultValue = "20") size: Int = 20,
    ): ApiResponse<AnalyticsActivityPageResponse<AnalyticsActivityEventResponse>> {
        val result = getEvents(AnalyticsActivityQuery(from, to, parseEventName(eventName), page, size), memberId)
        return ApiResponse.success(
            AnalyticsActivityPageResponse(
                result.items.map {
                    AnalyticsActivityEventResponse(
                        it.eventId,
                        it.eventName.name.lowercase(),
                        it.occurredAt,
                        it.targetType?.name?.lowercase(),
                        it.targetId,
                        !it.deduplicationKey.startsWith("RAW:") &&
                            it.eventName != UserAnalyticsEventName.SIGN_UP,
                    )
                },
                result.total,
                result.page,
                result.size,
            ),
        )
    }

    private fun parseEventName(value: String?): UserAnalyticsEventName? = value?.let { name ->
        requireNotNull(UserAnalyticsEventName.entries.find { it.name.equals(name, ignoreCase = true) }) {
            "Unknown analytics event"
        }
    }
}

data class AnalyticsActivityPageResponse<T>(
    @field:Schema(description = "현재 페이지 내역") val items: List<T>,
    @field:Schema(description = "필터와 일치하는 전체 수. 신규 활동 발생 시 변할 수 있음") val total: Long,
    @field:Schema(description = "현재 페이지 (0부터)") val page: Int,
    @field:Schema(description = "페이지 크기") val size: Int,
)

data class AnalyticsActivityMemberResponse(
    @field:Schema(description = "회원 식별자") val memberId: Long,
    @field:Schema(description = "현재 닉네임. 탈퇴·삭제 회원은 null") val nickname: String?,
    @field:Schema(description = "선택 기간·이벤트 적재 건수") val events: Long,
    @field:Schema(description = "선택 조건의 마지막 활동 시각") val lastEventAt: Instant,
)

data class AnalyticsActivityEventResponse(
    @field:Schema(description = "이벤트 식별자") val eventId: String,
    @field:Schema(description = "이벤트 이름") val eventName: String,
    @field:Schema(description = "발생 시각") val occurredAt: Instant,
    @field:Schema(description = "대상 종류") val targetType: String?,
    @field:Schema(description = "대상 ID") val targetId: Long?,
    @field:Schema(description = "이전 중복 제거 정책 기록 여부") val legacy: Boolean,
)
