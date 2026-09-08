package org.every.nook.api.application.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AnalyticsCoverage(val firstEventAt: Instant?, val lastEventAt: Instant?)

data class AnalyticsReport(
    val coverage: AnalyticsCoverage,
    val summary: AnalyticsSummary,
    val events: List<UserAnalyticsOverview.Behavior>,
    val saveDistribution: List<SaveBucket>,
    val saveReach: List<SaveReach>,
    val savedContentReturn: SavedContentReturn,
    val cohorts: List<AnalyticsCohort>,
    val dailyEvents: List<DailyAnalyticsEvents>,
    val members: List<MemberAnalytics> = emptyList(),
)

data class AnalyticsSummary(
    val activeUsers: Long,
    val signUps: Long,
    val usersWithoutSignUp: Long,
    val events: Long,
    val legacyEvents: Long,
    val savingUsers: Long,
    val uniqueSavedTargets: Long,
)

data class SaveBucket(val label: String, val users: Long)

data class SaveReach(val threshold: Int, val users: Long, val totalUsers: Long)

data class SavedContentReturn(val savingUsers: Long, val returnedUsers: Long, val revisitedTargets: Long)

data class AnalyticsCohort(
    val activatedOn: LocalDate,
    val users: Long,
    val retention: List<UserAnalyticsOverview.Retention>,
)

data class DailyAnalyticsEvents(val date: LocalDate, val events: List<UserAnalyticsOverview.Behavior>)

data class MemberAnalytics(
    val memberId: Long,
    val uniquePostSaves: Long,
    val uniquePlaceSaves: Long,
    val activeDays: Long,
    val lastEventAt: Instant,
    val events: List<UserAnalyticsOverview.Behavior>,
)

internal fun buildAnalyticsReport(
    periodEvents: List<UserAnalyticsEvent>,
    observedEvents: List<UserAnalyticsEvent>,
    coverage: AnalyticsCoverage,
    observedThrough: LocalDate,
    activations: Map<Long, LocalDate>,
): AnalyticsReport {
    val members = periodEvents.groupBy(UserAnalyticsEvent::memberId)
    val savedCounts = members.mapValues { (_, events) -> events.filter(::isSave).distinctBy(::target).size }
    val signUps = periodEvents.filter { it.eventName == UserAnalyticsEventName.SIGN_UP }
        .map(UserAnalyticsEvent::memberId).toSet()
    return AnalyticsReport(
        coverage = coverage,
        summary = AnalyticsSummary(
            activeUsers = members.size.toLong(),
            signUps = signUps.size.toLong(),
            usersWithoutSignUp = (members.keys - signUps).size.toLong(),
            events = periodEvents.size.toLong(),
            legacyEvents = periodEvents.count {
                it.eventName != UserAnalyticsEventName.SIGN_UP && !it.deduplicationKey.startsWith("RAW:")
            }.toLong(),
            savingUsers = savedCounts.values.count { it > 0 }.toLong(),
            uniqueSavedTargets = savedCounts.values.sumOf(Int::toLong),
        ),
        events = eventBreakdown(periodEvents),
        saveDistribution = SAVE_BUCKETS.map { (label, range) ->
            SaveBucket(label, savedCounts.values.count { it in range }.toLong())
        },
        saveReach = SAVE_THRESHOLDS.map { threshold ->
            SaveReach(threshold, savedCounts.values.count { it >= threshold }.toLong(), members.size.toLong())
        },
        savedContentReturn = savedContentReturn(periodEvents, observedEvents),
        cohorts = activationCohorts(activations, observedEvents, observedThrough),
        dailyEvents = periodEvents.groupBy(::eventDate).toSortedMap().map { (date, events) ->
            DailyAnalyticsEvents(date, eventBreakdown(events))
        },
        members = memberAnalytics(periodEvents),
    )
}

private fun memberAnalytics(events: List<UserAnalyticsEvent>): List<MemberAnalytics> =
    events.groupBy(UserAnalyticsEvent::memberId).map { (memberId, memberEvents) ->
        val saves = memberEvents.filter(::isSave).distinctBy(::target)
        MemberAnalytics(
            memberId = memberId,
            uniquePostSaves = saves.count { it.targetType == UserAnalyticsTargetType.POST }.toLong(),
            uniquePlaceSaves = saves.count { it.targetType == UserAnalyticsTargetType.PLACE }.toLong(),
            activeDays = memberEvents.map(::eventDate).distinct().size.toLong(),
            lastEventAt = memberEvents.maxOf(UserAnalyticsEvent::occurredAt),
            events = eventBreakdown(memberEvents),
        )
    }.sortedWith(
        compareByDescending<MemberAnalytics> { it.uniquePostSaves + it.uniquePlaceSaves }
            .thenBy(MemberAnalytics::memberId),
    ).take(MEMBER_LIMIT)

private fun savedContentReturn(
    periodEvents: List<UserAnalyticsEvent>,
    observedEvents: List<UserAnalyticsEvent>,
): SavedContentReturn {
    val saves = periodEvents.filter(::isSave).groupBy { Triple(it.memberId, it.targetType, it.targetId) }
        .mapValues { (_, events) -> events.minOf(::eventDate) }
    val returned = observedEvents.filter { event ->
        val savedOn = saves[Triple(event.memberId, event.targetType, event.targetId)]
        event.eventName in DETAIL_EVENTS && savedOn != null && eventDate(event) > savedOn
    }
    return SavedContentReturn(
        savingUsers = saves.keys.map { it.first }.distinct().size.toLong(),
        returnedUsers = returned.map(UserAnalyticsEvent::memberId).distinct().size.toLong(),
        revisitedTargets = returned.distinctBy { Triple(it.memberId, it.targetType, it.targetId) }.size.toLong(),
    )
}

private fun activationCohorts(
    activations: Map<Long, LocalDate>,
    events: List<UserAnalyticsEvent>,
    observedThrough: LocalDate,
): List<AnalyticsCohort> {
    val behaviorDates = events.filter { it.eventName != UserAnalyticsEventName.SIGN_UP }
        .groupBy(UserAnalyticsEvent::memberId).mapValues { (_, values) -> values.map(::eventDate).toSet() }
    return activations.entries.groupBy { it.value }.toSortedMap().map { (date, members) ->
        AnalyticsCohort(
            activatedOn = date,
            users = members.size.toLong(),
            retention = RETENTION_DAYS.map { day ->
                val targetDate = date.plusDays(day.toLong())
                val eligible = if (targetDate <= observedThrough) members else emptyList()
                UserAnalyticsOverview.Retention(
                    day,
                    eligible.size.toLong(),
                    eligible.count { targetDate in behaviorDates[it.key].orEmpty() }.toLong(),
                )
            },
        )
    }
}

private fun eventBreakdown(events: List<UserAnalyticsEvent>): List<UserAnalyticsOverview.Behavior> {
    val grouped = events.groupBy(UserAnalyticsEvent::eventName)
    return UserAnalyticsEventName.entries.map { name ->
        val matching = grouped[name].orEmpty()
        UserAnalyticsOverview.Behavior(
            name,
            matching.map(UserAnalyticsEvent::memberId).distinct().size.toLong(),
            matching.size.toLong(),
        )
    }
}

private fun isSave(event: UserAnalyticsEvent): Boolean = event.eventName in SAVE_EVENTS && event.targetId != null

private fun target(event: UserAnalyticsEvent): Pair<UserAnalyticsTargetType?, Long?> =
    event.targetType to event.targetId

private fun eventDate(event: UserAnalyticsEvent): LocalDate = event.occurredAt.atZone(REPORT_ZONE).toLocalDate()

private val REPORT_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
private const val MEMBER_LIMIT = 100
private val SAVE_EVENTS = setOf(UserAnalyticsEventName.POST_SAVE, UserAnalyticsEventName.PLACE_SAVE)
private val DETAIL_EVENTS = setOf(UserAnalyticsEventName.POST_VIEW, UserAnalyticsEventName.PLACE_VIEW)
private val SAVE_THRESHOLDS = listOf(1, 3, 5, 10)
private val RETENTION_DAYS = listOf(1, 7, 30)
private val SAVE_BUCKETS = listOf(
    "0개" to 0..0,
    "1–2개" to 1..2,
    "3–4개" to 3..4,
    "5–9개" to 5..9,
    "10개 이상" to 10..Int.MAX_VALUE,
)
