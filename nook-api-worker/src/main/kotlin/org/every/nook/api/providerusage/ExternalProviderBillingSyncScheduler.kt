package org.every.nook.api.providerusage

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.every.nook.api.application.providerusage.SyncExternalProviderBillingUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@Component
@ConditionalOnProperty(prefix = "external-provider.billing", name = ["sync-enabled"], havingValue = "true")
class ExternalProviderBillingSyncScheduler(
    private val syncBilling: SyncExternalProviderBillingUseCase,
    private val clock: Clock,
) {
    @Scheduled(cron = "\${external-provider.billing.sync-cron:0 0 * * * *}", zone = "Asia/Seoul")
    @SchedulerLock(
        name = "externalProviderBillingSync",
        lockAtLeastFor = "1m",
        lockAtMostFor = "30m",
    )
    fun sync() {
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, SEOUL)
        val start = today.withDayOfMonth(1)
        val summary = syncBilling(ExternalProviderBillingPeriod(start, start.plusMonths(1)), now)
        if (summary.failed > 0) {
            logger.warn { "External provider billing sync completed with failures: $summary" }
        } else {
            logger.info { "External provider billing sync completed: $summary" }
        }
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val logger = KotlinLogging.logger {}
    }
}
