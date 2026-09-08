package org.every.nook.api.application.analytics

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AnalyticsReportTest {
    @Test
    fun `includes existing members and separates repeated saves from unique targets`() {
        val events = listOf(
            event("POST_SAVE", 1, "2026-09-06T01:00:00Z"),
            event("POST_SAVE", 1, "2026-09-06T02:00:00Z"),
            event("PLACE_SAVE", 1, "2026-09-06T03:00:00Z"),
            event("ARCHIVE_VIEW", 2, "2026-09-06T04:00:00Z"),
            event("SIGN_UP", 3, "2026-09-06T05:00:00Z"),
        )
        val overview = overview(events)
        val report = assertNotNull(overview.report)
        assertEquals(3, report.summary.activeUsers)
        assertEquals(1, report.summary.signUps)
        assertEquals(2, report.summary.usersWithoutSignUp)
        assertEquals(5, report.summary.events)
        assertEquals(2, report.summary.uniqueSavedTargets)
        assertEquals(1, report.summary.savingUsers)
        assertEquals(0, report.summary.legacyEvents)
        assertEquals(2, report.events.single { it.eventName == UserAnalyticsEventName.POST_SAVE }.events)
        assertEquals(1, report.events.single { it.eventName == UserAnalyticsEventName.POST_SAVE }.users)
        assertEquals(2, report.saveDistribution.single { it.label == "0개" }.users)
        assertEquals(1, report.saveReach.single { it.threshold == 1 }.users)
        assertEquals(0, report.saveReach.single { it.threshold == 3 }.users)
        assertEquals(7, report.events.size)
        assertEquals(0, report.events.single { it.eventName == UserAnalyticsEventName.MAP_VIEW }.events)
        assertEquals(1, report.members.first().uniquePostSaves)
        assertEquals(1, report.members.first().uniquePlaceSaves)
        assertEquals(0, overview.funnel.activatedUsers)
    }

    @Test
    fun `follows selected signups beyond period without expanding period activity`() {
        val events = listOf(
            event("SIGN_UP", 1, "2026-09-06T01:00:00Z"),
            event("POST_SAVE", 1, "2026-09-07T01:00:00Z"),
            event("POST_VIEW", 1, "2026-09-08T01:00:00Z"),
            event("SIGN_UP", 2, "2026-09-07T01:00:00Z"),
            event("POST_SAVE", 2, "2026-09-07T02:00:00Z"),
        )
        val before = overview(events)
        val after = overview(events, observationEnd = "2026-09-08")
        assertEquals(0, before.funnel.activatedUsers)
        assertEquals(1, after.funnel.signUps)
        assertEquals(1, after.funnel.activatedUsers)
        assertEquals(1, after.funnel.returnedUsers)
        assertEquals(1, after.report?.summary?.events)
        assertEquals(1, after.daily.single().activeUsers)
        assertEquals(1, after.retention.single { it.day == 1 }.returnedUsers)
        val cohort = assertNotNull(after.report).cohorts.single()
        assertEquals(LocalDate.parse("2026-09-07"), cohort.activatedOn)
        assertEquals(1, cohort.retention.single { it.day == 1 }.returnedUsers)
        assertEquals(0, cohort.retention.single { it.day == 7 }.eligibleUsers)
    }

    @Test
    fun `revisit requires same member and target on later KST day`() {
        val events = listOf(
            event("POST_SAVE", 1, "2026-09-06T14:59:00Z"),
            event("POST_VIEW", 1, "2026-09-06T14:59:59Z"),
            event("POST_VIEW", 2, "2026-09-06T15:00:00Z"),
            event("PLACE_VIEW", 1, "2026-09-06T15:00:00Z"),
            event("POST_VIEW", 1, "2026-09-06T15:00:01Z", targetId = 2),
            event("POST_VIEW", 1, "2026-09-07T15:00:00Z"),
            event("POST_VIEW", 1, "2026-09-07T16:00:00Z"),
        )
        val before = assertNotNull(overview(events, observationEnd = "2026-09-07").report)
        val after = assertNotNull(overview(events, observationEnd = "2026-09-08").report)
        assertEquals(0, before.savedContentReturn.returnedUsers)
        assertEquals(SavedContentReturn(1, 1, 1), after.savedContentReturn)
        assertEquals(2, after.summary.events)
        assertEquals(1, after.summary.activeUsers)
    }

    @Test
    fun `uses half open KST day range and coverage independent of selection`() {
        val events = listOf(
            event("MAP_VIEW", 1, "2026-09-05T14:59:59Z"),
            event("MAP_VIEW", 2, "2026-09-05T15:00:00Z"),
            event("MAP_VIEW", 3, "2026-09-06T14:59:59Z"),
            event("MAP_VIEW", 4, "2026-09-06T15:00:00Z"),
        )
        val result = overview(events)
        assertEquals(2, result.activeUsers.daily)
        assertEquals(2, result.report?.summary?.events)
        assertEquals(events.first().occurredAt, result.report?.coverage?.firstEventAt)
        assertEquals(events.last().occurredAt, result.report?.coverage?.lastEventAt)
        val historical = overview(events, start = "2026-08-01", observationEnd = "2026-08-01")
        assertEquals(0, historical.activeUsers.daily)
        assertEquals(0, historical.report?.summary?.activeUsers)
        assertEquals(events.first().occurredAt, historical.report?.coverage?.firstEventAt)
    }

    @Test
    fun `distinguishes legacy records and retains empty event categories`() {
        val legacy = event("POST_VIEW", 1, "2026-09-06T01:00:00Z").copy(deduplicationKey = "old-key")
        assertEquals(1, overview(listOf(legacy)).report?.summary?.legacyEvents)
        val empty = assertNotNull(overview(emptyList()).report)
        assertEquals(AnalyticsCoverage(null, null), empty.coverage)
        assertEquals(7, empty.events.size)
        assertEquals(0, empty.summary.activeUsers)
        assertEquals(emptyList(), empty.cohorts)
    }

    @Test
    fun `rejects reversed or excessive observation periods`() {
        assertFailsWith<IllegalArgumentException> { overview(emptyList(), observationEnd = "2026-09-05") }
        assertFailsWith<IllegalArgumentException> { overview(emptyList(), observationEnd = "2027-09-06") }
    }

    private fun overview(
        events: List<UserAnalyticsEvent>,
        start: String = "2026-09-06",
        observationEnd: String = start,
    ): UserAnalyticsOverview {
        val query = object : UserAnalyticsEventQueryPort {
            override fun findAll(from: Instant, to: Instant) = events.filter {
                it.occurredAt >= from &&
                    it.occurredAt < to
            }
            override fun coverage() = AnalyticsCoverage(
                events.minOfOrNull { it.occurredAt },
                events.maxOfOrNull { it.occurredAt },
            )
        }
        return GetUserAnalyticsOverviewUseCase(
            query,
            Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC),
        )(
            UserAnalyticsPeriod(
                LocalDate.parse(start),
                LocalDate.parse(start),
                1,
                observationEnd = LocalDate.parse(observationEnd),
            ),
        )
    }

    private fun event(name: String, memberId: Long, at: String, targetId: Long = 1): UserAnalyticsEvent {
        val targetType = when {
            name.startsWith("POST") -> UserAnalyticsTargetType.POST
            name.startsWith("PLACE") -> UserAnalyticsTargetType.PLACE
            else -> null
        }
        return UserAnalyticsEvent(
            "$name-$memberId-$at",
            "RAW:$name-$memberId-$at",
            UserAnalyticsEventName.valueOf(name),
            memberId,
            targetType,
            targetId.takeIf { targetType != null },
            Instant.parse(at),
        )
    }
}
