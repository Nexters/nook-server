package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.every.nook.api.application.analytics.GetUserAnalyticsOverviewUseCase
import org.every.nook.api.application.analytics.UserAnalyticsOverview
import org.every.nook.api.application.analytics.UserAnalyticsPeriod
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

@Tag(name = "AdminUserAnalytics")
@Validated
@RestController
@RequestMapping("/api/admin/v1/user-analytics")
class AdminUserAnalyticsController(private val getOverview: GetUserAnalyticsOverviewUseCase) {
    @Operation(summary = "사용자 가입·활성화·재방문 분석 조회")
    @GetMapping
    fun overview(
        @Parameter(description = "전체 활동 및 가입 cohort 조회 시작일")
        @RequestParam
        from: LocalDate,
        @Parameter(description = "전체 활동 및 가입 cohort 조회 종료일. 해당 날짜를 포함합니다.")
        @RequestParam
        to: LocalDate,
        @Parameter(description = "활성 사용자로 판단할 서로 다른 저장 대상 개수")
        @RequestParam(defaultValue = "3")
        @Min(1)
        @Max(MAX_ACTIVATION_THRESHOLD)
        activationThreshold: Int,
        @Parameter(description = "DAU, WAU, MAU를 계산할 기준일. 생략하면 조회 종료일을 사용합니다.")
        @RequestParam(required = false)
        activeDate: LocalDate? = null,
        @Parameter(description = "가입 후 활성화·재방문 관측 종료일. 생략하면 to. 조회 시작부터 최대 365일.")
        @RequestParam(required = false)
        observationEnd: LocalDate? = null,
    ): ApiResponse<UserAnalyticsOverviewResponse> = ApiResponse.success(
        UserAnalyticsOverviewResponse.from(
            getOverview(UserAnalyticsPeriod(from, to, activationThreshold, activeDate ?: to, observationEnd ?: to)),
        ),
    )

    private companion object {
        const val MAX_ACTIVATION_THRESHOLD = 20L
    }
}

data class UserAnalyticsOverviewResponse(
    @field:Schema(description = "조회 시작일")
    val from: LocalDate,
    @field:Schema(description = "조회 종료일")
    val to: LocalDate,
    @field:Schema(description = "후속 관측 종료 기준일. 오늘 수치는 진행 중입니다.")
    val observedThrough: LocalDate,
    @field:Schema(description = "활성화에 필요한 서로 다른 저장 대상 개수")
    val activationThreshold: Int,
    @field:Schema(description = "조회 기간에 포함된 첫 이벤트 시각")
    val firstEventAt: Instant?,
    @field:Schema(description = "가입·활성화·재방문 퍼널")
    val funnel: FunnelResponse,
    @field:Schema(description = "선택한 활성 기준일의 서버 관측 활성 사용자")
    val activeUsers: ActiveUsersResponse,
    @field:Schema(description = "일별 가입·활성화·행동 사용자 추이")
    val daily: List<DailyResponse>,
    @field:Schema(description = "활성화 cohort의 exact-day 재방문")
    val retention: List<RetentionResponse>,
    @field:Schema(description = "활성화 이후 재방문 행동 구성")
    val behaviors: List<BehaviorResponse>,
    @field:Schema(description = "전체 회원의 기간 활동 및 수집 범위")
    val report: AnalyticsReportResponse? = null,
) {
    data class FunnelResponse(
        @field:Schema(description = "신규 가입 회원 수")
        val signUps: Long,
        @field:Schema(description = "저장 기준을 충족한 회원 수")
        val activatedUsers: Long,
        @field:Schema(description = "활성화 다음 날 이후 다시 행동한 회원 수")
        val returnedUsers: Long,
    )

    data class ActiveUsersResponse(
        @field:Schema(description = "활성 사용자 집계 기준일")
        val asOf: LocalDate,
        @field:Schema(description = "기준일 활성 사용자 수")
        val daily: Long,
        @field:Schema(description = "기준일 포함 최근 7일 활성 사용자 수")
        val weekly: Long,
        @field:Schema(description = "기준일 포함 최근 30일 활성 사용자 수")
        val monthly: Long,
    )

    data class DailyResponse(
        @field:Schema(description = "집계 일자")
        val date: LocalDate,
        @field:Schema(description = "해당 일자의 신규 가입 회원 수")
        val signUps: Long,
        @field:Schema(description = "해당 일자에 처음 활성화된 회원 수")
        val activatedUsers: Long,
        @field:Schema(description = "해당 일자에 분석 대상 행동을 한 회원 수")
        val activeUsers: Long,
    )

    data class RetentionResponse(
        @field:Schema(description = "활성화 이후 경과 일수")
        val day: Int,
        @field:Schema(description = "해당 날짜까지 관측 가능한 활성 회원 수")
        val eligibleUsers: Long,
        @field:Schema(description = "해당 날짜에 다시 행동한 회원 수")
        val returnedUsers: Long,
    )

    data class BehaviorResponse(
        @field:Schema(description = "재방문 행동 이벤트 이름")
        val eventName: String,
        @field:Schema(description = "해당 행동을 한 고유 회원 수")
        val users: Long,
        @field:Schema(description = "저장된 이벤트 수. 이전 일별·대상별 중복 제거 기록이 포함될 수 있습니다.")
        val events: Long,
    )

    companion object {
        fun from(overview: UserAnalyticsOverview) = UserAnalyticsOverviewResponse(
            from = overview.from,
            to = overview.to,
            observedThrough = overview.observedThrough,
            activationThreshold = overview.activationThreshold,
            firstEventAt = overview.firstEventAt,
            funnel = FunnelResponse(
                signUps = overview.funnel.signUps,
                activatedUsers = overview.funnel.activatedUsers,
                returnedUsers = overview.funnel.returnedUsers,
            ),
            activeUsers = ActiveUsersResponse(
                asOf = overview.activeUsers.asOf,
                daily = overview.activeUsers.daily,
                weekly = overview.activeUsers.weekly,
                monthly = overview.activeUsers.monthly,
            ),
            daily = overview.daily.map { daily ->
                DailyResponse(daily.date, daily.signUps, daily.activatedUsers, daily.activeUsers)
            },
            retention = overview.retention.map { retention ->
                RetentionResponse(retention.day, retention.eligibleUsers, retention.returnedUsers)
            },
            behaviors = overview.behaviors.map { behavior ->
                BehaviorResponse(behavior.eventName.name.lowercase(), behavior.users, behavior.events)
            },
            report = overview.report?.let(AnalyticsReportResponse::from),
        )
    }
}
