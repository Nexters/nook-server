package org.every.nook.api.application.analytics

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReliableUserAnalyticsEventRecorderTest {
    @Test
    fun `preserves repeated views and saves with independent raw event keys`() {
        val stored = mutableListOf<UserAnalyticsEvent>()
        val recorder = ReliableUserAnalyticsEventRecorder(
            storePort = UserAnalyticsEventStorePort(stored::add),
            clock = Clock.fixed(Instant.parse("2026-08-01T16:00:00Z"), ZoneOffset.UTC),
        )

        listOf(UserAnalyticsEventName.POST_VIEW, UserAnalyticsEventName.POST_SAVE).forEach { name ->
            val record = UserAnalyticsRecord(name, 7, UserAnalyticsTargetType.POST, 11)
            recorder.record(record)
            recorder.record(record)
            assertNotEquals(stored[stored.lastIndex - 1].deduplicationKey, stored.last().deduplicationKey)
        }
        stored.forEach { assertEquals("RAW:${it.eventId}", it.deduplicationKey) }
    }

    @Test
    fun `keeps signup semantic deduplication compatible with stored records`() {
        val stored = mutableListOf<UserAnalyticsEvent>()
        val recorder = ReliableUserAnalyticsEventRecorder(UserAnalyticsEventStorePort(stored::add), Clock.systemUTC())
        repeat(2) { recorder.record(UserAnalyticsRecord(UserAnalyticsEventName.SIGN_UP, 7)) }
        stored.forEach { assertEquals("SIGN_UP:MEMBER:7:NONE:0", it.deduplicationKey) }
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
