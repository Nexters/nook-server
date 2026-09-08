package org.every.nook.api.admin

import org.every.nook.api.application.analytics.AnalyticsActivityMember
import org.every.nook.api.application.analytics.AnalyticsActivityPage
import org.every.nook.api.application.analytics.AnalyticsActivityQuery
import org.every.nook.api.application.analytics.AnalyticsActivityQueryPort
import org.every.nook.api.application.analytics.GetAnalyticsActivityMembersUseCase
import org.every.nook.api.application.analytics.GetAnalyticsMemberEventsUseCase
import org.every.nook.api.application.analytics.UserAnalyticsEvent
import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminAnalyticsActivityControllerTest {
    private val port = FakePort()
    private val controller = AdminAnalyticsActivityController(
        GetAnalyticsActivityMembersUseCase(port),
        GetAnalyticsMemberEventsUseCase(port),
    )
    private val date = LocalDate.parse("2026-09-09")

    @Test
    fun `passes range filter and pagination without restricting to old top savers`() {
        val result = controller.members(date, date, "post_view", page = 5, size = 20).success!!
        assertEquals(AnalyticsActivityQuery(date, date, UserAnalyticsEventName.POST_VIEW, 5, 20), port.query)
        assertEquals(105L, result.total)
        assertEquals(5, result.page)
        assertEquals(20, result.size)
        assertEquals("회원", result.items.single().nickname)
    }

    @Test
    fun `serializes event names targets and legacy markers and rejects invalid inputs`() {
        val result = controller.events(date, date, 7).success!!
        assertEquals(7L, port.memberId)
        assertEquals(listOf("post_view", "post_view", "sign_up"), result.items.map { it.eventName })
        assertEquals(listOf(false, true, false), result.items.map { it.legacy })
        assertEquals("post", result.items.first().targetType)
        assertEquals(42L, result.items.first().targetId)
        assertFailsWith<IllegalArgumentException> { controller.members(date, date, "first_open") }
        assertFailsWith<IllegalArgumentException> { controller.events(date, date, 0) }
        assertFailsWith<IllegalArgumentException> { controller.members(date, date, size = 101) }
    }

    private class FakePort : AnalyticsActivityQueryPort {
        var query: AnalyticsActivityQuery? = null
        var memberId: Long? = null
        private val at = Instant.parse("2026-09-09T00:00:00Z")

        override fun members(query: AnalyticsActivityQuery): AnalyticsActivityPage<AnalyticsActivityMember> {
            this.query = query
            return AnalyticsActivityPage(listOf(AnalyticsActivityMember(7, "회원", 3, at)), 105, query.page, query.size)
        }

        override fun events(query: AnalyticsActivityQuery, memberId: Long): AnalyticsActivityPage<UserAnalyticsEvent> {
            this.memberId = memberId
            val event = UserAnalyticsEvent(
                "event",
                "RAW:event",
                UserAnalyticsEventName.POST_VIEW,
                memberId,
                UserAnalyticsTargetType.POST,
                42,
                at,
            )
            return AnalyticsActivityPage(
                listOf(
                    event,
                    event.copy(deduplicationKey = "old"),
                    event.copy(eventName = UserAnalyticsEventName.SIGN_UP),
                ),
                3,
                query.page,
                query.size,
            )
        }
    }
}
