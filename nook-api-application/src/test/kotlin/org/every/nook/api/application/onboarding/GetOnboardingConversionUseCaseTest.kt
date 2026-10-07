package org.every.nook.api.application.onboarding

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GetOnboardingConversionUseCaseTest {
    private val start = Instant.parse("2026-10-01T15:00:00Z")
    private val period = OnboardingPeriod(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-02"))

    @Test
    fun `includes exact time boundaries and separates immature users from completed cohorts`() {
        val journeys = listOf(
            journey(0, listOf(300)),
            journey(0, listOf(600)),
            journey(0, listOf(1800)),
            journey(0, listOf(-1, 86401)),
            journey(86300, listOf(86350, 86401)),
        )
        val result = report(journeys)
        assertEquals(5, result.clickedUsers)
        assertEquals(listOf(2, 3, 4), result.windows.map { it.convertedUsers })
        assertEquals(listOf(4, 4, 4), result.windows.map { it.eligibleUsers })
        assertEquals(listOf(1, 2, 3), result.windows.map { it.eligibleConvertedUsers })
        assertEquals(4, result.completedDayUsers)
        assertEquals(600.0, result.medianFirstSaveSeconds)
        assertEquals(listOf(1, 3, 0, 0), result.distribution.map { it.users })
    }

    @Test
    fun `counts distinct posts even after delete and resave and includes 24 hour boundary`() {
        val saves = listOf(
            OnboardingSave(1, start.plusSeconds(1)),
            OnboardingSave(1, start.plusSeconds(2)),
            OnboardingSave(2, start.plusSeconds(86400)),
            OnboardingSave(3, start.plusSeconds(86401)),
        )
        val result = report(listOf(OnboardingJourney(start, saves, emptyList()), journey(0, listOf(3, 4, 5))))
        assertEquals(listOf(0, 0, 1, 1), result.distribution.map { it.users })
        assertEquals(2.0, result.medianFirstSaveSeconds)
    }

    @Test
    fun `extension funnel requires ordered events within CTA observation window`() {
        val journeys = listOf(
            journey(0, listOf(50, 200, 400), listOf(-1, 100, 300)),
            journey(0, listOf(20, 86401), listOf(100)),
            journey(86300, listOf(86350), listOf(86301)),
        )
        val funnel = report(journeys).extension!!
        assertEquals(2, funnel.enteredUsers)
        assertEquals(1, funnel.savedUsers)
        assertEquals(100.0, funnel.medianEntrySeconds)
        assertEquals(100.0, funnel.medianSaveSeconds)
    }

    @Test
    fun `empty or unobserved cohorts do not manufacture medians or extension rates`() {
        val empty = report(emptyList())
        assertEquals(0, empty.clickedUsers)
        assertNull(empty.medianFirstSaveSeconds)
        assertNull(empty.extension)
        assertEquals(listOf(0, 0, 0, 0), empty.distribution.map { it.users })
        val pending = report(listOf(journey(86400, listOf(86400))))
        assertEquals(0, pending.completedDayUsers)
        assertNull(pending.medianFirstSaveSeconds)
        assertEquals(listOf(0, 0, 0), pending.windows.map { it.eligibleUsers })
    }

    @Test
    fun `KST date selection is inclusive and capped at 90 days`() {
        assertEquals(start, period.fromInstant)
        assertEquals(start.plusSeconds(86400), period.toExclusive)
        assertFailsWith<IllegalArgumentException> { OnboardingPeriod(period.to, period.from.minusDays(1)) }
        assertFailsWith<IllegalArgumentException> { OnboardingPeriod(period.from, period.from.plusDays(90)) }
    }

    private fun journey(click: Long, saves: List<Long>, entries: List<Long> = emptyList()) = OnboardingJourney(
        start.plusSeconds(click),
        saves.mapIndexed { index, seconds -> OnboardingSave(index.toLong(), start.plusSeconds(seconds)) },
        entries.map(start::plusSeconds),
    )

    private fun report(journeys: List<OnboardingJourney>) = GetOnboardingConversionUseCase(
        OnboardingConversionQueryPort { _, _ -> journeys },
        Clock.fixed(start.plusSeconds(86400), ZoneOffset.UTC),
    )(period)
}
