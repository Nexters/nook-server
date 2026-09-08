package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.analytics.AnalyticsReport
import org.every.nook.api.application.analytics.UserAnalyticsOverview
import java.time.Instant
import java.time.LocalDate

data class AnalyticsReportResponse(
    @field:Schema(description = "전체 이벤트 최초·최종 시각. 연속 수집을 보장하지 않습니다.")
    val coverage: CoverageResponse,
    @field:Schema(description = "선택 기간 전체 회원의 활동 요약")
    val summary: SummaryResponse,
    @field:Schema(description = "전체 회원 이벤트별 고유 회원·적재 건수")
    val events: List<UserAnalyticsOverviewResponse.BehaviorResponse>,
    @field:Schema(description = "활동 회원별 기간 내 서로 다른 저장 대상 수 분포. 0개 포함")
    val saveDistribution: List<SaveBucketResponse>,
    @field:Schema(description = "전체 기간 활동 회원 중 저장 개수 기준 달성")
    val saveReach: List<SaveReachResponse>,
    @field:Schema(description = "기간 내 저장한 동일 대상을 다음 날 이후 상세 조회한 회원")
    val savedContentReturn: SavedContentReturnResponse,
    @field:Schema(description = "선택 기간 가입자의 활성화일별 exact-day 재방문")
    val cohorts: List<CohortResponse>,
    @field:Schema(description = "이벤트가 있는 일자별 이벤트 내역. 없는 날짜는 빈 내역")
    val dailyEvents: List<DailyEventsResponse>,
    @field:Schema(description = "기간 저장 대상 수 내림차순 상위 100명. 동률은 회원 ID 오름차순")
    val members: List<MemberResponse> = emptyList(),
) {
    data class CoverageResponse(
        @field:Schema(description = "전체 최초 이벤트 시각") val firstEventAt: Instant?,
        @field:Schema(description = "전체 최종 이벤트 시각") val lastEventAt: Instant?,
    )

    data class SummaryResponse(
        @field:Schema(description = "기간 중 이벤트를 남긴 고유 회원 수") val activeUsers: Long,
        @field:Schema(description = "기간 중 가입 이벤트를 남긴 고유 회원 수") val signUps: Long,
        @field:Schema(description = "기간 내 가입 기록 없는 활동 회원. 과거 가입 여부는 보장하지 않음")
        val usersWithoutSignUp: Long,
        @field:Schema(description = "기간 중 적재된 전체 이벤트 수") val events: Long,
        @field:Schema(description = "이전 중복 제거 정책으로 적재된 가입 외 이벤트 수") val legacyEvents: Long,
        @field:Schema(description = "기간 중 저장한 고유 회원 수") val savingUsers: Long,
        @field:Schema(description = "회원별 서로 다른 저장 대상 수 합계. 현재 보관 수와 다름")
        val uniqueSavedTargets: Long,
    )

    data class SaveBucketResponse(
        @field:Schema(description = "서로 다른 저장 대상 개수 구간") val label: String,
        @field:Schema(description = "해당 구간 회원 수") val users: Long,
    )

    data class SaveReachResponse(
        @field:Schema(description = "최소 저장 대상 개수") val threshold: Int,
        @field:Schema(description = "달성한 회원 수") val users: Long,
        @field:Schema(description = "분모인 기간 활동 회원 수") val totalUsers: Long,
    )

    data class SavedContentReturnResponse(
        @field:Schema(description = "선택 기간 저장 회원 수") val savingUsers: Long,
        @field:Schema(description = "관측 종료일까지 동일 콘텐츠를 다음 날 이후 상세 조회한 회원 수")
        val returnedUsers: Long,
        @field:Schema(description = "회원별 다시 본 저장 대상 수 합계") val revisitedTargets: Long,
    )

    data class CohortResponse(
        @field:Schema(description = "활성화 일자") val activatedOn: LocalDate,
        @field:Schema(description = "해당 일자 활성화 회원 수") val users: Long,
        @field:Schema(
            description = "활성화일 기준 exact-day 재방문",
        ) val retention: List<UserAnalyticsOverviewResponse.RetentionResponse>,
    )

    data class DailyEventsResponse(
        @field:Schema(description = "KST 집계 일자") val date: LocalDate,
        @field:Schema(description = "해당 날짜 이벤트별 회원·적재 수")
        val events: List<UserAnalyticsOverviewResponse.BehaviorResponse>,
    )

    data class MemberResponse(
        @field:Schema(description = "회원 식별자") val memberId: Long,
        @field:Schema(description = "기간 내 서로 다른 저장 게시물 수") val uniquePostSaves: Long,
        @field:Schema(description = "기간 내 서로 다른 저장 장소 수") val uniquePlaceSaves: Long,
        @field:Schema(description = "기간 내 활동 일수") val activeDays: Long,
        @field:Schema(description = "기간 내 마지막 이벤트 시각") val lastEventAt: Instant,
        @field:Schema(description = "회원별 이벤트 내역")
        val events: List<UserAnalyticsOverviewResponse.BehaviorResponse>,
    )

    companion object {
        fun from(report: AnalyticsReport) = AnalyticsReportResponse(
            coverage = CoverageResponse(report.coverage.firstEventAt, report.coverage.lastEventAt),
            summary = report.summary.let {
                SummaryResponse(
                    it.activeUsers,
                    it.signUps,
                    it.usersWithoutSignUp,
                    it.events,
                    it.legacyEvents,
                    it.savingUsers,
                    it.uniqueSavedTargets,
                )
            },
            events = report.events.map(::behaviorResponse),
            saveDistribution = report.saveDistribution.map { SaveBucketResponse(it.label, it.users) },
            saveReach = report.saveReach.map { SaveReachResponse(it.threshold, it.users, it.totalUsers) },
            savedContentReturn = report.savedContentReturn.let {
                SavedContentReturnResponse(it.savingUsers, it.returnedUsers, it.revisitedTargets)
            },
            cohorts = report.cohorts.map { cohort ->
                CohortResponse(
                    cohort.activatedOn,
                    cohort.users,
                    cohort.retention.map {
                        UserAnalyticsOverviewResponse.RetentionResponse(it.day, it.eligibleUsers, it.returnedUsers)
                    },
                )
            },
            dailyEvents = report.dailyEvents.map { daily ->
                DailyEventsResponse(
                    daily.date,
                    daily.events.map(::behaviorResponse),
                )
            },
            members = report.members.map { member ->
                MemberResponse(
                    member.memberId,
                    member.uniquePostSaves,
                    member.uniquePlaceSaves,
                    member.activeDays,
                    member.lastEventAt,
                    member.events.map(::behaviorResponse),
                )
            },
        )

        private fun behaviorResponse(behavior: UserAnalyticsOverview.Behavior) =
            UserAnalyticsOverviewResponse.BehaviorResponse(
                behavior.eventName.name.lowercase(),
                behavior.users,
                behavior.events,
            )
    }
}
