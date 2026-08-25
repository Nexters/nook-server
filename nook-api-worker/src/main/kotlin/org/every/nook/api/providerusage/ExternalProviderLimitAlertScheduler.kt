package org.every.nook.api.providerusage

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.providerusage.EvaluateExternalProviderLimitAlertsUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@Component
@ConditionalOnProperty(prefix = "external-provider.limits", name = ["alert-enabled"], havingValue = "true")
class ExternalProviderLimitAlertScheduler(
    private val evaluateAlerts: EvaluateExternalProviderLimitAlertsUseCase,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${external-provider.limits.alert-interval:1m}")
    @SchedulerLock(
        name = "externalProviderLimitAlerts",
        lockAtLeastFor = "5s",
        lockAtMostFor = "55s",
    )
    fun evaluate() {
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, SEOUL)
        val periodStart = today.withDayOfMonth(1)
        val from = periodStart.atStartOfDay(SEOUL).toInstant()
        val to = periodStart.plusMonths(1).atStartOfDay(SEOUL).toInstant()
        runCatching { evaluateAlerts(from, to, periodStart, now) }
            .onSuccess { count ->
                if (count > 0) logger.info { "External provider limit alerts delivered: count=$count" }
            }
            .onFailure { exception -> logger.warn(exception) { "Failed to evaluate external provider limit alerts" } }
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val logger = KotlinLogging.logger {}
    }
}
