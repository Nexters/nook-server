package org.every.nook.api.application.onboarding

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class OnboardingPeriod(val from: LocalDate, val to: LocalDate) {
    init {
        require(to >= from && ChronoUnit.DAYS.between(from, to) < MAX_DAYS) {
            "CTA cohort range must be 1 to 90 days"
        }
    }

    val fromInstant: Instant get() = from.atStartOfDay(ZONE).toInstant()
    val toExclusive: Instant get() = to.plusDays(1).atStartOfDay(ZONE).toInstant()

    private companion object {
        const val MAX_DAYS = 90
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

data class OnboardingSave(val postId: Long, val occurredAt: Instant)

data class OnboardingJourney(
    val clickedAt: Instant,
    val saves: List<OnboardingSave>,
    val extensionEntries: List<Instant>,
)

fun interface OnboardingConversionQueryPort {
    // Select each member's first-ever CTA, then filter that timestamp by the requested period.
    fun journeys(period: OnboardingPeriod, observedAt: Instant): List<OnboardingJourney>
}

data class OnboardingWindow(
    val minutes: Long,
    val convertedUsers: Int,
    val eligibleUsers: Int,
    val eligibleConvertedUsers: Int,
)

data class OnboardingDistribution(val posts: String, val users: Int)

data class OnboardingExtensionFunnel(
    val enteredUsers: Int,
    val savedUsers: Int,
    val medianEntrySeconds: Double?,
    val medianSaveSeconds: Double?,
)

data class OnboardingConversion(
    val period: OnboardingPeriod,
    val observedAt: Instant,
    val clickedUsers: Int,
    val windows: List<OnboardingWindow>,
    val completedDayUsers: Int,
    val medianFirstSaveSeconds: Double?,
    val distribution: List<OnboardingDistribution>,
    val extension: OnboardingExtensionFunnel?,
)
