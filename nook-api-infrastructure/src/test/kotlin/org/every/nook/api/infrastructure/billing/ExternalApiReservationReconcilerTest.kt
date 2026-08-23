package org.every.nook.api.infrastructure.billing

import org.every.nook.api.infrastructure.persistence.billing.ExternalApiUsageEventJpaRepository
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class ExternalApiReservationReconcilerTest {
    @Test
    fun `marks reservations older than the timeout as failed`() {
        var cutoff: Instant? = null
        val repository = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ExternalApiUsageEventJpaRepository::class.java),
        ) { _, method, arguments ->
            if (method.name == "expireReservations") cutoff = arguments[3] as Instant
            0
        } as ExternalApiUsageEventJpaRepository
        val before = Instant.now().minus(Duration.ofHours(1)).minusSeconds(1)

        ExternalApiReservationReconciler(repository, Duration.ofHours(1)).reconcile()

        assertTrue(requireNotNull(cutoff).isAfter(before))
    }
}
