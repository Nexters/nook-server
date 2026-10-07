package org.every.nook.api.admin

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.onboarding.OnboardingConversion
import java.time.Instant
import java.time.LocalDate

data class OnboardingConversionResponse(
    @field:Schema(description = "최초 CTA 발생일 조회 시작일 KST")
    val from: LocalDate,
    @field:Schema(description = "최초 CTA 발생일 조회 종료일 KST, 포함")
    val to: LocalDate,
    @field:Schema(description = "후속 저장 관측 시각 UTC. 지연 수신 이벤트에 의해 이후 수정될 수 있습니다.")
    val observedAt: Instant,
    @field:Schema(description = "선택 기간에 최초 CTA를 클릭한 고유 회원 수")
    val clickedUsers: Int,
    @field:Schema(description = "5분, 10분, 30분 전환. 잠정값과 관측 완료 집단을 구분합니다.")
    val windows: List<Window>,
    @field:Schema(description = "CTA 이후 24시간 관측이 완료된 회원 수")
    val completedDayUsers: Int,
    @field:Schema(description = "24시간 관측 미완료 회원 수. 0개 구간에서 제외합니다.")
    val pendingDayUsers: Int,
    @field:Schema(description = "24시간 관측 완료 집단 중 24시간 내 저장 전환자의 첫 저장 초 중앙값. 없으면 null.")
    val medianFirstSaveSeconds: Double?,
    @field:Schema(description = "24시간 관측 완료 집단의 서로 다른 게시물 저장 개수 분포")
    val distribution: List<Distribution>,
    @field:Schema(description = "24시간 관측 완료 집단의 Extension 퍼널. 진입 기록이 없으면 null, 미수집과 구분 불가.")
    val extension: Extension?,
) {
    data class Window(
        @field:Schema(description = "CTA 이후 관측 시간(분)") val minutes: Long,
        @field:Schema(description = "시간 내 첫 저장 회원 수, 관측 중인 회원 포함") val convertedUsers: Int,
        @field:Schema(description = "전체 클릭자 대비 잠정 전환율(%). 분모 0이면 null") val provisionalRate: Double?,
        @field:Schema(description = "해당 시간 관측 완료 회원 수") val eligibleUsers: Int,
        @field:Schema(description = "해당 시간 관측 미완료 회원 수") val pendingUsers: Int,
        @field:Schema(description = "관측 완료 회원 중 시간 내 저장한 회원 수") val eligibleConvertedUsers: Int,
        @field:Schema(description = "관측 완료 집단의 전환율(%). 분모 0이면 null") val completedRate: Double?,
    )

    data class Distribution(
        @field:Schema(description = "서로 다른 게시물 수 구간: 0, 1, 2, 3+") val posts: String,
        @field:Schema(description = "구간에 속한 회원 수") val users: Int,
    )

    data class Extension(
        @field:Schema(description = "CTA 이후 24시간 내 첫 Extension 진입 회원 수") val enteredUsers: Int,
        @field:Schema(description = "24시간 관측 완료 CTA 회원 대비 진입률(%)") val entryRate: Double?,
        @field:Schema(description = "Extension 첫 진입 이후 CTA 기준 24시간 내 저장 회원 수") val savedUsers: Int,
        @field:Schema(description = "진입 회원 대비 저장률(%)") val saveRate: Double?,
        @field:Schema(description = "CTA에서 첫 진입까지 초 중앙값") val medianEntrySeconds: Double?,
        @field:Schema(description = "첫 진입에서 이후 첫 저장까지 초 중앙값") val medianSaveSeconds: Double?,
    )

    companion object {
        fun from(result: OnboardingConversion) = OnboardingConversionResponse(
            from = result.period.from,
            to = result.period.to,
            observedAt = result.observedAt,
            clickedUsers = result.clickedUsers,
            windows = result.windows.map {
                Window(
                    it.minutes,
                    it.convertedUsers,
                    rate(it.convertedUsers, result.clickedUsers),
                    it.eligibleUsers,
                    result.clickedUsers - it.eligibleUsers,
                    it.eligibleConvertedUsers,
                    rate(it.eligibleConvertedUsers, it.eligibleUsers),
                )
            },
            completedDayUsers = result.completedDayUsers,
            pendingDayUsers = result.clickedUsers - result.completedDayUsers,
            medianFirstSaveSeconds = result.medianFirstSaveSeconds,
            distribution = result.distribution.map { Distribution(it.posts, it.users) },
            extension = result.extension?.let {
                Extension(
                    it.enteredUsers,
                    rate(it.enteredUsers, result.completedDayUsers),
                    it.savedUsers,
                    rate(it.savedUsers, it.enteredUsers),
                    it.medianEntrySeconds,
                    it.medianSaveSeconds,
                )
            },
        )

        private fun rate(count: Int, total: Int): Double? = if (total == 0) null else count.toDouble() / total * PERCENT

        private const val PERCENT = 100
    }
}
