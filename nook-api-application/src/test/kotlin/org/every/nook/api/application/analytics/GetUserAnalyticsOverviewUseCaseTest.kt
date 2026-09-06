package org.every.nook.api.application.analytics

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class GetUserAnalyticsOverviewUseCaseTest {
    @Test
    fun `calculates activation and exact day retention for signup cohort`() {
        val events = listOf(
            event(UserAnalyticsEventName.SIGN_UP, 1, null, null, "2026-08-01T01:00:00Z"),
            event(UserAnalyticsEventName.POST_SAVE, 1, UserAnalyticsTargetType.POST, 11, "2026-08-01T02:00:00Z"),
            event(UserAnalyticsEventName.PLACE_SAVE, 1, UserAnalyticsTargetType.PLACE, 21, "2026-08-01T03:00:00Z"),
            event(UserAnalyticsEventName.POST_SAVE, 1, UserAnalyticsTargetType.POST, 12, "2026-08-01T04:00:00Z"),
            event(UserAnalyticsEventName.POST_VIEW, 1, UserAnalyticsTargetType.POST, 11, "2026-08-02T01:00:00Z"),
            event(UserAnalyticsEventName.SIGN_UP, 2, null, null, "2026-08-01T05:00:00Z"),
            event(UserAnalyticsEventName.POST_SAVE, 2, UserAnalyticsTargetType.POST, 31, "2026-08-01T06:00:00Z"),
        )
        val useCase = GetUserAnalyticsOverviewUseCase(
            queryPort = UserAnalyticsEventQueryPort { _, _ -> events },
            clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC),
        )

        val overview = useCase(
            UserAnalyticsPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01"), 3),
        )

        assertEquals(2, overview.funnel.signUps)
        assertEquals(1, overview.funnel.activatedUsers)
        assertEquals(1, overview.funnel.returnedUsers)
        assertEquals(1, overview.retention.single { it.day == 1 }.returnedUsers)
        assertEquals(1, overview.behaviors.single().users)
        assertEquals(UserAnalyticsEventName.POST_VIEW, overview.behaviors.single().eventName)
    }

    @Test
    fun `counts repeated saves of the same target only once for activation`() {
        val events = listOf(
            event(UserAnalyticsEventName.SIGN_UP, 1, null, null, "2026-08-01T01:00:00Z"),
            event(UserAnalyticsEventName.POST_SAVE, 1, UserAnalyticsTargetType.POST, 11, "2026-08-01T02:00:00Z"),
            event(UserAnalyticsEventName.POST_SAVE, 1, UserAnalyticsTargetType.POST, 11, "2026-08-02T02:00:00Z"),
            event(UserAnalyticsEventName.PLACE_SAVE, 1, UserAnalyticsTargetType.PLACE, 21, "2026-08-02T03:00:00Z"),
        )
        val overview = GetUserAnalyticsOverviewUseCase(
            UserAnalyticsEventQueryPort { _, _ -> events },
            Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC),
        )(UserAnalyticsPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-09"), 3))

        assertEquals(0, overview.funnel.activatedUsers)
    }

    @Test
    fun `calculates daily weekly and monthly active users at the observed date`() {
        val events = listOf(
            event(UserAnalyticsEventName.SIGN_UP, 1, null, null, "2026-09-06T00:00:00Z"),
            event(UserAnalyticsEventName.ARCHIVE_VIEW, 2, null, null, "2026-09-01T00:00:00Z"),
            event(UserAnalyticsEventName.POST_VIEW, 3, UserAnalyticsTargetType.POST, 11, "2026-08-10T00:00:00Z"),
            event(UserAnalyticsEventName.PLACE_VIEW, 4, UserAnalyticsTargetType.PLACE, 21, "2026-08-01T00:00:00Z"),
        )
        val overview = GetUserAnalyticsOverviewUseCase(
            UserAnalyticsEventQueryPort { _, _ -> events },
            Clock.fixed(Instant.parse("2026-09-06T01:00:00Z"), ZoneOffset.UTC),
        )(UserAnalyticsPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-06"), 3))

        assertEquals(LocalDate.parse("2026-09-06"), overview.activeUsers.asOf)
        assertEquals(1, overview.activeUsers.daily)
        assertEquals(2, overview.activeUsers.weekly)
        assertEquals(3, overview.activeUsers.monthly)
    }

    private fun event(
        name: UserAnalyticsEventName,
        memberId: Long,
        targetType: UserAnalyticsTargetType?,
        targetId: Long?,
        occurredAt: String,
    ) = UserAnalyticsEvent(
        eventId = "$name-$memberId-$occurredAt",
        deduplicationKey = "$name-$memberId-$targetType-$targetId-$occurredAt",
        eventName = name,
        memberId = memberId,
        targetType = targetType,
        targetId = targetId,
        occurredAt = Instant.parse(occurredAt),
    )
}
