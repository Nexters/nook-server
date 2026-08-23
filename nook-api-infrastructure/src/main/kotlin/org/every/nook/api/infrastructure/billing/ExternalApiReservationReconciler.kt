package org.every.nook.api.infrastructure.billing

import mu.KotlinLogging
import org.every.nook.api.infrastructure.persistence.billing.ExternalApiUsageEventJpaRepository
import org.every.nook.api.infrastructure.persistence.billing.UsageEventStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Component
class ExternalApiReservationReconciler(
    private val repository: ExternalApiUsageEventJpaRepository,
    @Value("\${external-api-pricing.reservation-timeout:1h}") private val reservationTimeout: Duration,
) {
    private val clock = Clock.systemUTC()

    @Scheduled(fixedDelayString = "\${external-api-pricing.reservation-reconcile-interval:10m}")
    @Transactional
    fun reconcile() {
        val now = clock.instant()
        val count = repository.expireReservations(
            reservedStatus = UsageEventStatus.RESERVED,
            failedStatus = UsageEventStatus.FAILED,
            failureCode = STALE_RESERVATION_FAILURE_CODE,
            cutoff = now.minus(reservationTimeout),
            settledAt = now,
        )
        if (count > 0) logger.warn { "Stale external API reservations reconciled: count=$count" }
    }

    private companion object {
        const val STALE_RESERVATION_FAILURE_CODE = "STALE_RESERVATION"
        val logger = KotlinLogging.logger {}
    }
}
