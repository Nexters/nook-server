package org.every.nook.api.application.analytics

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ReliableUserAnalyticsEventRecorderTest {
    @Test
    fun `uses a daily deduplication key for view events`() {
        val stored = mutableListOf<UserAnalyticsEvent>()
        val recorder = ReliableUserAnalyticsEventRecorder(
            storePort = UserAnalyticsEventStorePort(stored::add),
            clock = Clock.fixed(Instant.parse("2026-08-01T16:00:00Z"), ZoneOffset.UTC),
        )

        recorder.record(
            UserAnalyticsRecord(
                UserAnalyticsEventName.POST_VIEW,
                7,
                targetType = UserAnalyticsTargetType.POST,
                targetId = 11,
            ),
        )

        assertEquals("POST_VIEW:MEMBER:7:POST:11:2026-08-02", stored.single().deduplicationKey)
    }

    @Test
    fun `keeps core behavior successful when analytics storage fails`() {
        val recorder = ReliableUserAnalyticsEventRecorder(
            storePort = UserAnalyticsEventStorePort { error("analytics unavailable") },
            clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC),
        )

        recorder.record(UserAnalyticsRecord(UserAnalyticsEventName.SIGN_UP, 7))
    }
}
