package org.every.nook.api.application.analytics

import mu.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class UserAnalyticsEventName {
    SIGN_UP,
    POST_SAVE,
    PLACE_SAVE,
    ARCHIVE_VIEW,
    POST_VIEW,
    PLACE_VIEW,
    MAP_VIEW,
}

enum class UserAnalyticsTargetType {
    POST,
    PLACE,
    GROUP,
}

data class UserAnalyticsEvent(
    val eventId: String,
    val deduplicationKey: String,
    val eventName: UserAnalyticsEventName,
    val memberId: Long,
    val targetType: UserAnalyticsTargetType?,
    val targetId: Long?,
    val occurredAt: Instant,
)

fun interface UserAnalyticsEventStorePort {
    fun store(event: UserAnalyticsEvent)
}

fun interface UserAnalyticsEventQueryPort {
    fun findAll(from: Instant, to: Instant): List<UserAnalyticsEvent>
}

data class UserAnalyticsRecord(
    val eventName: UserAnalyticsEventName,
    val memberId: Long,
    val targetType: UserAnalyticsTargetType? = null,
    val targetId: Long? = null,
)

fun interface UserAnalyticsEventRecorder {
    fun record(record: UserAnalyticsRecord)

    companion object {
        val NONE = UserAnalyticsEventRecorder { }
    }
}

class ReliableUserAnalyticsEventRecorder(
    private val storePort: UserAnalyticsEventStorePort,
    private val clock: Clock,
) : UserAnalyticsEventRecorder {
    override fun record(record: UserAnalyticsRecord) {
        val occurredAt = clock.instant()
        runCatching {
            storePort.store(
                UserAnalyticsEvent(
                    eventId = UUID.randomUUID().toString(),
                    deduplicationKey = serverDeduplicationKey(record, occurredAt),
                    eventName = record.eventName,
                    memberId = record.memberId,
                    targetType = record.targetType,
                    targetId = record.targetId,
                    occurredAt = occurredAt,
                ),
            )
        }.onFailure { cause ->
            logger.warn(cause) {
                "Failed to record user analytics event: event=${record.eventName}, memberId=${record.memberId}"
            }
        }
    }

    private fun serverDeduplicationKey(record: UserAnalyticsRecord, occurredAt: Instant): String {
        val target = "${record.targetType?.name ?: "NONE"}:${record.targetId ?: 0L}"
        return if (record.eventName in DAILY_EVENTS) {
            val date = occurredAt.atZone(ADMIN_ZONE).toLocalDate()
            "${record.eventName}:MEMBER:${record.memberId}:$target:$date"
        } else {
            "${record.eventName}:MEMBER:${record.memberId}:$target"
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val ADMIN_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val DAILY_EVENTS = setOf(
            UserAnalyticsEventName.ARCHIVE_VIEW,
            UserAnalyticsEventName.POST_VIEW,
            UserAnalyticsEventName.PLACE_VIEW,
            UserAnalyticsEventName.MAP_VIEW,
        )
    }
}

data class UserAnalyticsPeriod(val start: LocalDate, val endInclusive: LocalDate, val activationThreshold: Int)

data class UserAnalyticsOverview(
    val from: LocalDate,
    val to: LocalDate,
    val observedThrough: LocalDate,
    val activationThreshold: Int,
    val firstEventAt: Instant?,
    val funnel: Funnel,
    val activeUsers: ActiveUsers,
    val daily: List<Daily>,
    val retention: List<Retention>,
    val behaviors: List<Behavior>,
) {
    data class Funnel(val signUps: Long, val activatedUsers: Long, val returnedUsers: Long)

    data class ActiveUsers(val asOf: LocalDate, val daily: Long, val weekly: Long, val monthly: Long)

    data class Daily(val date: LocalDate, val signUps: Long, val activatedUsers: Long, val activeUsers: Long)

    data class Retention(val day: Int, val eligibleUsers: Long, val returnedUsers: Long)

    data class Behavior(val eventName: UserAnalyticsEventName, val users: Long, val events: Long)
}

class GetUserAnalyticsOverviewUseCase(private val queryPort: UserAnalyticsEventQueryPort, private val clock: Clock) {
    operator fun invoke(period: UserAnalyticsPeriod): UserAnalyticsOverview {
        validate(period)
        val observedThrough = minOf(period.endInclusive, clock.instant().atZone(ADMIN_ZONE).toLocalDate())
        val events = loadEvents(period)
        val signUps = signUps(events)
        val activationByMember = activations(events, signUps, period.activationThreshold)
        val returnEvents = returnEvents(events, activationByMember)

        return UserAnalyticsOverview(
            from = period.start,
            to = period.endInclusive,
            observedThrough = observedThrough,
            activationThreshold = period.activationThreshold,
            firstEventAt = events.minOfOrNull(UserAnalyticsEvent::occurredAt),
            funnel = UserAnalyticsOverview.Funnel(
                signUps = signUps.size.toLong(),
                activatedUsers = activationByMember.size.toLong(),
                returnedUsers = returnEvents.map(UserAnalyticsEvent::memberId).distinct().size.toLong(),
            ),
            activeUsers = activeUsers(queryPort, observedThrough),
            daily = daily(period, signUps, activationByMember, events),
            retention = retention(activationByMember, events, observedThrough),
            behaviors = behaviors(returnEvents),
        )
    }

    private fun loadEvents(period: UserAnalyticsPeriod): List<UserAnalyticsEvent> = queryPort.findAll(
        period.start.atStartOfDay(ADMIN_ZONE).toInstant(),
        period.endInclusive.plusDays(1).atStartOfDay(ADMIN_ZONE).toInstant(),
    ).sortedBy(UserAnalyticsEvent::occurredAt)

    private fun signUps(events: List<UserAnalyticsEvent>): Map<Long, UserAnalyticsEvent> =
        events.filter { it.eventName == UserAnalyticsEventName.SIGN_UP }
            .groupBy(UserAnalyticsEvent::memberId)
            .mapValues { (_, memberEvents) -> memberEvents.minBy(UserAnalyticsEvent::occurredAt) }

    private fun activations(
        events: List<UserAnalyticsEvent>,
        signUps: Map<Long, UserAnalyticsEvent>,
        threshold: Int,
    ): Map<Long, LocalDate> = signUps.mapNotNull { (memberId, signUp) ->
        events.asSequence()
            .filter { event ->
                event.memberId == memberId &&
                    event.eventName in SAVE_EVENTS &&
                    event.occurredAt >= signUp.occurredAt
            }
            .distinctBy { it.targetType to it.targetId }
            .sortedBy(UserAnalyticsEvent::occurredAt)
            .drop(threshold - 1)
            .firstOrNull()
            ?.let { memberId to it.occurredAt.atZone(ADMIN_ZONE).toLocalDate() }
    }.toMap()

    private fun returnEvents(
        events: List<UserAnalyticsEvent>,
        activationByMember: Map<Long, LocalDate>,
    ): List<UserAnalyticsEvent> = events.filter { event ->
        val activatedAt = activationByMember[event.memberId] ?: return@filter false
        event.eventName in RETURN_EVENTS && event.occurredAt.atZone(ADMIN_ZONE).toLocalDate() > activatedAt
    }

    private fun daily(
        period: UserAnalyticsPeriod,
        signUps: Map<Long, UserAnalyticsEvent>,
        activationByMember: Map<Long, LocalDate>,
        events: List<UserAnalyticsEvent>,
    ): List<UserAnalyticsOverview.Daily> = dates(period).map { date ->
        UserAnalyticsOverview.Daily(
            date = date,
            signUps = signUps.values.count {
                it.occurredAt.atZone(ADMIN_ZONE).toLocalDate() == date
            }.toLong(),
            activatedUsers = activationByMember.values.count { it == date }.toLong(),
            activeUsers = events.asSequence()
                .filter { event ->
                    event.eventName in RETURN_EVENTS &&
                        event.occurredAt.atZone(ADMIN_ZONE).toLocalDate() == date
                }
                .map(UserAnalyticsEvent::memberId)
                .distinct()
                .count()
                .toLong(),
        )
    }

    private fun dates(period: UserAnalyticsPeriod): List<LocalDate> = generateSequence(period.start) { date ->
        date.plusDays(1).takeUnless { it > period.endInclusive }
    }.toList()

    private fun retention(
        activationByMember: Map<Long, LocalDate>,
        events: List<UserAnalyticsEvent>,
        observedThrough: LocalDate,
    ): List<UserAnalyticsOverview.Retention> = RETENTION_DAYS.map { day ->
        val eligible = activationByMember.filterValues { activatedAt ->
            !activatedAt.plusDays(day.toLong()).isAfter(observedThrough)
        }
        val returned = eligible.count { (memberId, activatedAt) ->
            events.any { event -> event.isReturnOn(memberId, activatedAt.plusDays(day.toLong())) }
        }
        UserAnalyticsOverview.Retention(day, eligible.size.toLong(), returned.toLong())
    }

    private fun UserAnalyticsEvent.isReturnOn(memberId: Long, date: LocalDate): Boolean = this.memberId == memberId &&
        eventName in RETURN_EVENTS &&
        occurredAt.atZone(ADMIN_ZONE).toLocalDate() == date

    private fun behaviors(returnEvents: List<UserAnalyticsEvent>): List<UserAnalyticsOverview.Behavior> =
        returnEvents.groupBy(UserAnalyticsEvent::eventName)
            .map { (eventName, behaviorEvents) ->
                UserAnalyticsOverview.Behavior(
                    eventName = eventName,
                    users = behaviorEvents.map(UserAnalyticsEvent::memberId).distinct().size.toLong(),
                    events = behaviorEvents.size.toLong(),
                )
            }.sortedByDescending(UserAnalyticsOverview.Behavior::users)

    private fun validate(period: UserAnalyticsPeriod) {
        require(!period.endInclusive.isBefore(period.start)) { "Analytics end date must not precede start date" }
        require(ChronoUnit.DAYS.between(period.start, period.endInclusive) < MAX_PERIOD_DAYS) {
            "Analytics period must be $MAX_PERIOD_DAYS days or fewer"
        }
        require(period.activationThreshold in MIN_ACTIVATION_THRESHOLD..MAX_ACTIVATION_THRESHOLD) {
            "Activation threshold must be between $MIN_ACTIVATION_THRESHOLD and $MAX_ACTIVATION_THRESHOLD"
        }
    }

    private companion object {
        const val MAX_PERIOD_DAYS = 90L
        const val MIN_ACTIVATION_THRESHOLD = 1
        const val MAX_ACTIVATION_THRESHOLD = 20
        val ADMIN_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val SAVE_EVENTS = setOf(UserAnalyticsEventName.POST_SAVE, UserAnalyticsEventName.PLACE_SAVE)
        val RETURN_EVENTS = UserAnalyticsEventName.entries.toSet() - UserAnalyticsEventName.SIGN_UP
        val RETENTION_DAYS = listOf(1, 7, 30)
    }
}

private fun activeUsers(queryPort: UserAnalyticsEventQueryPort, asOf: LocalDate): UserAnalyticsOverview.ActiveUsers {
    val from = asOf.minusDays(MONTHLY_WINDOW_DAYS - 1L)
    val events = queryPort.findAll(
        from.atStartOfDay(ANALYTICS_ZONE).toInstant(),
        asOf.plusDays(1).atStartOfDay(ANALYTICS_ZONE).toInstant(),
    )
    fun count(windowDays: Long): Long {
        val windowStart = asOf.minusDays(windowDays - 1L)
        return events.asSequence()
            .filter { event -> event.occurredAt.atZone(ANALYTICS_ZONE).toLocalDate() in windowStart..asOf }
            .map(UserAnalyticsEvent::memberId)
            .distinct()
            .count()
            .toLong()
    }
    return UserAnalyticsOverview.ActiveUsers(
        asOf = asOf,
        daily = count(1),
        weekly = count(WEEKLY_WINDOW_DAYS),
        monthly = count(MONTHLY_WINDOW_DAYS),
    )
}

private const val WEEKLY_WINDOW_DAYS = 7L
private const val MONTHLY_WINDOW_DAYS = 30L
private val ANALYTICS_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
