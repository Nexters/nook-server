package org.every.nook.api.application.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AnalyticsActivityQuery(
    val from: LocalDate,
    val to: LocalDate,
    val eventName: UserAnalyticsEventName? = null,
    val page: Int = 0,
    val size: Int = 20,
) {
    init {
        require(to >= from && ChronoUnit.DAYS.between(from, to) < MAX_DAYS) { "Activity range must be 1 to 90 days" }
        require(page in 0..MAX_PAGE && size in 1..MAX_SIZE) { "Invalid activity pagination" }
    }

    val fromInstant: Instant get() = from.atStartOfDay(ZONE).toInstant()
    val toExclusive: Instant get() = to.plusDays(1).atStartOfDay(ZONE).toInstant()

    private companion object {
        const val MAX_DAYS = 90
        const val MAX_PAGE = 100000
        const val MAX_SIZE = 100
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

data class AnalyticsActivityPage<T>(val items: List<T>, val total: Long, val page: Int, val size: Int)

data class AnalyticsActivityMember(
    val memberId: Long,
    val nickname: String?,
    val events: Long,
    val lastEventAt: Instant,
)

interface AnalyticsActivityQueryPort {
    fun members(query: AnalyticsActivityQuery): AnalyticsActivityPage<AnalyticsActivityMember>

    fun events(query: AnalyticsActivityQuery, memberId: Long): AnalyticsActivityPage<UserAnalyticsEvent>
}

class GetAnalyticsActivityMembersUseCase(private val queryPort: AnalyticsActivityQueryPort) {
    operator fun invoke(query: AnalyticsActivityQuery): AnalyticsActivityPage<AnalyticsActivityMember> =
        queryPort.members(query)
}

class GetAnalyticsMemberEventsUseCase(private val queryPort: AnalyticsActivityQueryPort) {
    operator fun invoke(query: AnalyticsActivityQuery, memberId: Long): AnalyticsActivityPage<UserAnalyticsEvent> {
        require(memberId > 0) { "Member ID must be positive" }
        return queryPort.events(query, memberId)
    }
}
