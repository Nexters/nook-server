package org.every.nook.api.infrastructure.persistence.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.every.nook.api.application.providerusage.ExternalProviderBillingSnapshot
import org.every.nook.api.application.providerusage.ExternalProviderBillingSyncResult
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalProviderBillingPersistenceAdapterTest {
    private val snapshots = mock(ExternalProviderBillingSnapshotJpaRepository::class.java)
    private val states = mock(ExternalProviderBillingSyncStateJpaRepository::class.java)
    private val adapter = ExternalProviderBillingPersistenceAdapter(snapshots, states)

    @Test
    fun `replaces every snapshot for the provider and period with the latest result`() {
        val period = ExternalProviderBillingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"))
        val succeededAt = Instant.parse("2026-09-02T14:00:00Z")
        val result = ExternalProviderBillingSyncResult(
            provider = "OPENAI",
            period = period,
            snapshots = listOf(
                ExternalProviderBillingSnapshot(
                    provider = "OPENAI",
                    sku = "gpt-5-mini-input",
                    period = period,
                    usageUnits = BigDecimal.ZERO,
                    costUsd = BigDecimal("0.0123"),
                    source = "OPENAI_COSTS_API",
                    sourceUpdatedAt = succeededAt,
                ),
            ),
        )
        `when`(states.findByProvider("OPENAI")).thenReturn(null)

        adapter.replace(result, succeededAt)

        verify(snapshots).deleteAllByProviderAndPeriod("OPENAI", period.start, period.end)
        @Suppress("UNCHECKED_CAST")
        val savedCaptor = ArgumentCaptor.forClass(List::class.java)
            as ArgumentCaptor<List<ExternalProviderBillingSnapshotEntity>>
        verify(snapshots).saveAll(savedCaptor.capture())
        assertEquals(listOf("gpt-5-mini-input"), savedCaptor.value.map { it.sku })
        assertEquals(listOf(BigDecimal("0.0123")), savedCaptor.value.map { it.costUsd })

        val stateCaptor = ArgumentCaptor.forClass(ExternalProviderBillingSyncStateEntity::class.java)
        verify(states).save(stateCaptor.capture())
        assertEquals("SUCCEEDED", stateCaptor.value.status)
        assertEquals(succeededAt, stateCaptor.value.lastSucceededAt)
    }

    @Test
    fun `removes stale snapshots even when the latest result is empty`() {
        val period = ExternalProviderBillingPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"))
        val succeededAt = Instant.parse("2026-09-02T14:00:00Z")
        val result = ExternalProviderBillingSyncResult("OPENAI", period, emptyList())

        adapter.replace(result, succeededAt)

        verify(snapshots).deleteAllByProviderAndPeriod("OPENAI", period.start, period.end)
        verify(snapshots).saveAll(emptyList<ExternalProviderBillingSnapshotEntity>())
    }
}
